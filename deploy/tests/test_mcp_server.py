"""Shared MCP deployment boundaries, rendered without cluster access.

Only the opt-in baseline test pulls the ilm-lib dependency from the registry.
"""
import base64
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

import yaml
import unittest

from helm_render import ROOT, render, render_path


def configured_values():
    return {
        'image': {'digest': 'sha256:' + 'a' * 64},
        'apiUrl': 'https://ilm.example.invalid/api',
        'security': {
            'issuer': 'https://identity.example.invalid/tenant',
            'resource': 'https://ilm.example.invalid/mcp',
            'jwkSetUri': 'https://identity.example.invalid/oauth/jwks',
            'tokenUri': 'https://identity.example.invalid/oauth/token',
            'clientId': 'ilm-mcp',
            'clientSecretRef': {'name': 'ilm-mcp-oauth', 'key': 'client-secret'},
            'apiAudience': 'ilm',
            'exchangeMode': 'standard',
            'authorizationScopes': ['profile'],
        },
        'networkPolicy': {
            'ingress': [{'namespaceSelector': {'matchLabels': {
                'kubernetes.io/metadata.name': 'edge'}},
                'podSelector': {'matchLabels': {'app.kubernetes.io/name': 'traefik'}}}],
            'egress': [{'to': [{'ipBlock': {'cidr': '192.0.2.20/32'}}],
                        'ports': [{'protocol': 'TCP', 'port': 443}]}],
        },
    }


ILM_GLOBALS = {'hostName': 'ilm.example.invalid', 'keycloak': {'enabled': True}}
EXTERNAL_PROVIDER = {
    'issuer': 'https://identity.example.invalid/tenant',
    'jwkSetUri': 'https://identity.example.invalid/oauth/jwks',
    'tokenUri': 'https://identity.example.invalid/oauth/token',
    'clientId': 'mcp-exchange',
    'clientSecretRef': {'name': 'mcp-oauth'},
    'exchangeMode': 'standard',
}


def umbrella_values(mode=None, **values):
    """Values as the ILM chart passes them with MCP turned on; without a mode the chart's default applies."""
    mcp = {'enabled': True, **({'identity': {'mode': mode}} if mode else {})}
    return {'global': {**copy.deepcopy(ILM_GLOBALS), 'mcp': mcp}, 'networkPolicy': {'enabled': False}, **values}


def external_values(**security):
    return umbrella_values('external', security={**copy.deepcopy(EXTERNAL_PROVIDER), **security})


# Each case patches configured values into values the chart refuses: a dict updates the map under its key, anything
# else replaces the key.
UNSAFE_INPUTS = (
    ('volumes', {'ephemeral': {'sizeLimit': ''}}),
    ('image', {'digest': 'unpublished'}),
    ('apiUrl', 'http://ilm.example.invalid/api'),
    ('apiUrl', None),
    ('apiUrl', ''),
    ('security', {'issuer': None}),
    ('security', {'clientSecretRef': None}),
    ('networkPolicy', {'ingress': None}),
    ('security', {'issuer': ''}),
    ('security', {'issuer': 'http://identity.example.invalid'}),
    ('security', {'resource': ''}),
    ('security', {'resource': 'https://ilm.example.invalid/mcp?token=x'}),
    ('security', {'jwkSetUri': ''}),
    ('security', {'tokenUri': ''}),
    ('security', {'clientId': ''}),
    ('security', {'clientSecretRef': {'name': '', 'key': 'client-secret'}}),
    ('security', {'clientSecret': 'SYNTHETIC_INLINE_SECRET'}),
    ('security', {'exchangeMode': 'fallback'}),
    ('security', {'apiAudience': 'https://ilm.example.invalid/mcp'}),
    ('security', {'jwsAlgorithms': ['HS256']}),
    ('security', {'jwsAlgorithms': ['none']}),
    ('security', {'allowedOrigins': ['https://example.invalid/path']}),
    ('access', 'administrator'),
    ('replicaCount', 0),
    ('networkPolicy', {'ingress': []}),
    ('networkPolicy', {'egress': []}),
    ('security', {'allowHttpBackchannel': 'false'}),
    ('security', {'allowHttpBackchannel': None}),
    ('networkPolicy', {'enabled': 'false'}),
    ('networkPolicy', {'enabled': None}),
    ('trusted', None),
    ('global', {'trusted': None}),
)


def patched(values, key, patch):
    if isinstance(patch, dict) and key in values:
        values[key].update(patch)
    else:
        values[key] = patch
    return values


def by_kind(objects, kind):
    return next(o for o in objects if o['kind'] == kind)


def mcp_container(objects):
    return by_kind(objects, 'Deployment')['spec']['template']['spec']['containers'][0]


def deployment_env(objects):
    return {entry['name']: entry.get('value') for entry in mcp_container(objects)['env']}


def trusted_certificates_entries(objects):
    return [entry for entry in mcp_container(objects)['env'] if entry['name'] == 'TRUSTED_CERTIFICATES']


def client_secret_ref(objects):
    entry = next(e for e in mcp_container(objects)['env'] if e['name'] == 'ILM_MCP_SECURITY_CLIENT_SECRET')
    return entry['valueFrom']['secretKeyRef']


def pod_annotations(objects):
    return by_kind(objects, 'Deployment')['spec']['template']['metadata'].get('annotations', {})


def scoped(values, scope):
    """The mapping that holds a setting in the given scope: the chart's own values or the shared globals."""
    return values if scope == 'local' else values.setdefault('global', {})


PROXY_ENV_NAMES = {'httpProxy': 'HTTP_PROXY', 'httpsProxy': 'HTTPS_PROXY', 'noProxy': 'NO_PROXY'}
SYNTHETIC_CA = '-----BEGIN CERTIFICATE-----\nc3ludGhldGljLWNh\n-----END CERTIFICATE-----\n'
OTHER_SYNTHETIC_CA = '-----BEGIN CERTIFICATE-----\nb3RoZXItc3ludGhldGlj\n-----END CERTIFICATE-----\n'


def render_ref(ref, values):
    """Renders the chart as committed at a git ref, using that ref's own locked dependencies."""
    with tempfile.TemporaryDirectory(prefix='ilm-mcp-baseline-') as temp:
        archive = subprocess.run(['git', 'archive', ref, 'deploy/charts/mcp-server'],
                                 cwd=ROOT, check=True, stdout=subprocess.PIPE).stdout
        subprocess.run(['tar', '-x', '-C', temp], check=True, input=archive)
        chart = Path(temp) / 'deploy/charts/mcp-server'
        subprocess.run([os.environ.get('HELM_BIN', 'helm'), 'dependency', 'build', str(chart)],
                       check=True, stdout=subprocess.DEVNULL)
        return render_path(chart, values)


class SharedMcpChartTests(unittest.TestCase):
    def assert_render_fails(self, values, expected):
        with self.assertRaises(AssertionError) as raised:
            render('mcp-server', values)
        self.assertIn(expected, str(raised.exception))

    def assert_env(self, values, expected):
        """Renders values, expects the given environment entries, and returns the rendered objects."""
        objects = render('mcp-server', values)
        env = deployment_env(objects)
        self.assertEqual({name: env.get(name) for name in expected}, expected)
        return objects

    def assert_unsafe_inputs_fail(self, values):
        """Patches each of UNSAFE_INPUTS into a fresh values() and expects the render to fail."""
        for key, patch in UNSAFE_INPUTS:
            with self.subTest(key=key, patch=patch):
                unsafe = patched(values(), key, patch)
                with self.assertRaises(AssertionError):
                    render('mcp-server', unsafe)

    def test_missing_required_configuration_fails_without_an_activation_switch(self):
        with self.assertRaises(AssertionError):
            render('mcp-server', {})

    def test_parent_activation_metadata_does_not_control_standalone_rendering(self):
        for enabled in (True, False):
            with self.subTest(enabled=enabled):
                values = configured_values()
                values['enabled'] = enabled
                self.assertIn('Deployment', [o['kind'] for o in render('mcp-server', values)])

    def test_parent_alias_and_condition_control_only_the_parent_dependency(self):
        with tempfile.TemporaryDirectory(prefix='ilm-mcp-parent-test-') as temp:
            parent = Path(temp)
            subchart = parent / 'charts/mcp-server'
            shutil.copytree(ROOT / 'deploy/charts/mcp-server', subchart)
            version = yaml.safe_load((subchart / 'Chart.yaml').read_text())['version']
            (parent / 'Chart.yaml').write_text(yaml.safe_dump({
                'apiVersion': 'v2', 'name': 'ilm', 'version': '0.1.0',
                'dependencies': [{'name': 'mcp-server', 'alias': 'mcpServer',
                                  'version': version, 'condition': 'mcpServer.enabled'}],
            }))
            values = configured_values()
            values['enabled'] = False
            self.assertEqual(render_path(parent, {'mcpServer': values}), [])
            values['enabled'] = True
            objects = render_path(parent, {'mcpServer': values, 'global': {
                'image': {'registry': 'mirror.example.invalid', 'repository': 'platform',
                          'pullSecrets': ['umbrella-pull']},
                'database': {'host': 'database'}, 'messaging': {'enabled': True},
                'trusted': {'certificates': 'public-ca'},
            }})
            deployment = next(o for o in objects if o['kind'] == 'Deployment')
            service = next(o for o in objects if o['kind'] == 'Service')
            policy = next(o for o in objects if o['kind'] == 'NetworkPolicy')
            labels = deployment['spec']['selector']['matchLabels']
            self.assertEqual(deployment['metadata']['name'], 'ilm-test-mcpserver')
            self.assertEqual(labels['app.kubernetes.io/name'], 'mcpserver')
            self.assertEqual(service['spec']['selector'], labels)
            self.assertEqual(policy['spec']['podSelector']['matchLabels'], labels)
            pod = deployment['spec']['template']['spec']
            self.assertEqual(pod['containers'][0]['image'],
                             'mirror.example.invalid/platform/mcp-server@sha256:' + 'a' * 64)
            self.assertEqual(pod['imagePullSecrets'], [{'name': 'umbrella-pull'}])

    def test_release_containing_chart_name_is_not_duplicated(self):
        values = configured_values()
        values['nameOverride'] = 'test'
        deployment = next(o for o in render('mcp-server', values) if o['kind'] == 'Deployment')
        self.assertEqual(deployment['metadata']['name'], 'ilm-test')

    def test_shared_workload_is_stateless_and_uses_only_secret_references(self):
        values = configured_values()
        objects = render('mcp-server', values)
        self.assertEqual({o['kind'] for o in objects}, {'Deployment', 'Service', 'NetworkPolicy', 'ServiceAccount'})
        deployment = next(o for o in objects if o['kind'] == 'Deployment')
        pod = deployment['spec']['template']['spec']
        container = pod['containers'][0]
        env = {entry['name']: entry for entry in container['env']}
        self.assertEqual(env['SPRING_AI_MCP_SERVER_PROTOCOL']['value'], 'STATELESS')
        self.assertEqual(env['ILM_AUTH_METHOD']['value'], 'oauth-delegation')
        self.assertEqual(env['ILM_MCP_SECURITY_ENABLED']['value'], 'true')
        self.assertEqual(env['ILM_MCP_SECURITY_EXCHANGE_MODE']['value'], 'standard')
        self.assertEqual(env['ILM_MCP_SECURITY_API_AUDIENCE']['value'], 'ilm')
        self.assertEqual(env['ILM_MCP_SECURITY_AUTHORIZATION_SCOPES']['value'],
                         'profile')
        self.assertEqual(env['ILM_MCP_ACCESS']['value'], 'read-only')
        self.assertEqual(env['ILM_MCP_SECURITY_CLIENT_SECRET']['valueFrom']['secretKeyRef'],
                         {'name': 'ilm-mcp-oauth', 'key': 'client-secret'})
        self.assertNotIn('value', env['ILM_MCP_SECURITY_CLIENT_SECRET'])
        self.assertTrue(container['image'].endswith('@' + values['image']['digest']))
        self.assertFalse(pod['automountServiceAccountToken'])
        self.assertEqual(pod['securityContext']['fsGroup'], 10001)
        self.assertTrue(container['securityContext']['readOnlyRootFilesystem'])
        self.assertFalse(container['securityContext']['allowPrivilegeEscalation'])
        self.assertEqual(container['securityContext']['capabilities']['drop'], ['ALL'])
        self.assertEqual([v['name'] for v in pod['volumes']], ['tmp'])
        self.assertNotIn('PersistentVolumeClaim', json.dumps(objects))
        self.assertNotIn('PRIVATE_JWK', json.dumps(objects))
        for probe in ('startupProbe', 'readinessProbe', 'livenessProbe'):
            self.assertTrue(container[probe]['httpGet']['path'].startswith('/actuator/health/'))

    def test_replicas_can_roll_without_sticky_sessions(self):
        values = configured_values()
        values['replicaCount'] = 2
        objects = render('mcp-server', values)
        deployment = next(o for o in objects if o['kind'] == 'Deployment')
        service = next(o for o in objects if o['kind'] == 'Service')
        self.assertEqual(deployment['spec']['replicas'], 2)
        self.assertEqual(deployment['spec']['strategy']['type'], 'RollingUpdate')
        self.assertNotIn('sessionAffinity', service['spec'])

    def test_actor_delegation_is_selected_explicitly(self):
        values = configured_values()
        values['security']['exchangeMode'] = 'delegation'
        values['security']['authorizationScopes'] = ['openid', 'delegation:client:ilm-mcp']
        container = next(o for o in render('mcp-server', values)
                         if o['kind'] == 'Deployment')['spec']['template']['spec']['containers'][0]
        env = {entry['name']: entry.get('value') for entry in container['env']}
        self.assertEqual(env['ILM_MCP_SECURITY_EXCHANGE_MODE'], 'delegation')
        self.assertEqual(env['ILM_MCP_SECURITY_AUTHORIZATION_SCOPES'],
                         'openid,delegation:client:ilm-mcp')
        self.assertNotIn('KC_FEATURES', env)

    def test_all_supported_asymmetric_signatures_are_configurable(self):
        values = configured_values()
        algorithms = ['RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512',
                      'PS256', 'PS384', 'PS512']
        values['security']['jwsAlgorithms'] = algorithms
        container = next(o for o in render('mcp-server', values)
                         if o['kind'] == 'Deployment')['spec']['template']['spec']['containers'][0]
        env = {entry['name']: entry.get('value') for entry in container['env']}
        self.assertEqual(env['ILM_MCP_SECURITY_JWS_ALGORITHMS'], ','.join(algorithms))

    def test_network_boundary_retains_configured_peers_and_dns(self):
        values = configured_values()
        deployment, policy = [next(o for o in render('mcp-server', values) if o['kind'] == kind)
                              for kind in ('Deployment', 'NetworkPolicy')]
        spec = policy['spec']
        self.assertEqual(spec['podSelector']['matchLabels'], deployment['spec']['selector']['matchLabels'])
        self.assertEqual(set(spec['policyTypes']), {'Ingress', 'Egress'})
        self.assertEqual(spec['ingress'][0]['from'], values['networkPolicy']['ingress'])
        self.assertEqual(spec['egress'][1:], values['networkPolicy']['egress'])
        self.assertEqual({p['protocol'] for p in spec['egress'][0]['ports']}, {'TCP', 'UDP'})
        self.assertNotIn('0.0.0.0/0', json.dumps(spec))


    def test_image_tag_and_app_version_fallback_allow_generic_installs(self):
        values = configured_values()
        values['image']['digest'] = ''
        values['image']['tag'] = 'reviewed'
        container = next(o for o in render('mcp-server', values)
                         if o['kind'] == 'Deployment')['spec']['template']['spec']['containers'][0]
        self.assertEqual(container['image'], 'hub.omnitrustregistry.com/ilm/mcp-server:reviewed')
        values['image']['tag'] = ''
        container = next(o for o in render('mcp-server', values)
                         if o['kind'] == 'Deployment')['spec']['template']['spec']['containers'][0]
        self.assertEqual(container['image'], 'hub.omnitrustregistry.com/ilm/mcp-server:0.1.0')
        values['image']['digest'] = 'sha256:' + 'a' * 64
        values['image']['tag'] = 'ignored'
        container = next(o for o in render('mcp-server', values)
                         if o['kind'] == 'Deployment')['spec']['template']['spec']['containers'][0]
        self.assertEqual(container['image'], 'hub.omnitrustregistry.com/ilm/mcp-server@sha256:' + 'a' * 64)

    def test_library_global_image_and_pull_secret_settings_are_applied(self):
        values = configured_values()
        values['global'] = {'image': {'registry': 'mirror.example.invalid',
                                     'repository': 'trusted', 'pullSecrets': ['global-pull']}}
        values['image']['pullSecrets'] = ['local-pull']
        pod = next(o for o in render('mcp-server', values)
                   if o['kind'] == 'Deployment')['spec']['template']['spec']
        self.assertEqual(pod['containers'][0]['image'],
                         'mirror.example.invalid/trusted/mcp-server@sha256:' + 'a' * 64)
        self.assertEqual(pod['imagePullSecrets'],
                         [{'name': 'global-pull'}, {'name': 'local-pull'}])

    def test_common_pod_and_container_controls_render(self):
        values = configured_values()
        values.update({
            'nameOverride': 'assistant',
            'podLabels': {'owner': 'platform'},
            'podAnnotations': {'example.invalid/review': '{{ .Release.Name }}'},
            'nodeSelector': {'kubernetes.io/os': 'linux'},
            'tolerations': [{'key': 'dedicated', 'operator': 'Equal', 'value': 'ilm', 'effect': 'NoSchedule'}],
            'affinity': {'nodeAffinity': {'preferredDuringSchedulingIgnoredDuringExecution': []}},
            'terminationGracePeriodSeconds': 45,
            'serviceAccount': {'create': True, 'name': 'mcp-identity', 'annotations': {'owner': 'platform'}},
            'volumes': {'ephemeral': {'sizeLimit': '64Mi', 'medium': 'Memory'}},
            'podSecurityContext': {'fsGroup': 20001},
        })
        values['image'].update({
            'command': ['/bin/server'], 'args': ['--name={{ .Release.Name }}'],
            'resources': {'requests': {'cpu': '200m', 'memory': '384Mi'}},
            'securityContext': {'runAsUser': 20001},
            'probes': {'startup': {'enabled': False},
                       'readiness': {'custom': {'tcpSocket': {'port': 'http'}}, 'periodSeconds': 7}},
        })
        objects = render('mcp-server', values)
        deployment = next(o for o in objects if o['kind'] == 'Deployment')
        service = next(o for o in objects if o['kind'] == 'Service')
        account = next(o for o in objects if o['kind'] == 'ServiceAccount')
        metadata = deployment['spec']['template']['metadata']
        pod = deployment['spec']['template']['spec']
        container = pod['containers'][0]
        self.assertEqual(deployment['metadata']['name'], 'ilm-test-assistant')
        self.assertEqual(service['spec']['selector'], deployment['spec']['selector']['matchLabels'])
        self.assertEqual(metadata['labels']['owner'], 'platform')
        self.assertEqual(metadata['annotations']['example.invalid/review'], 'ilm-test')
        for key in ('nodeSelector', 'tolerations', 'affinity', 'terminationGracePeriodSeconds'):
            self.assertEqual(pod[key], values[key])
        self.assertEqual(pod['serviceAccountName'], 'mcp-identity')
        self.assertEqual(account['metadata']['name'], 'mcp-identity')
        self.assertEqual(account['metadata']['annotations'], {'owner': 'platform'})
        self.assertFalse(account['automountServiceAccountToken'])
        self.assertEqual(pod['securityContext']['fsGroup'], 20001)
        self.assertEqual(container['securityContext']['runAsUser'], 20001)
        self.assertTrue(container['securityContext']['readOnlyRootFilesystem'])
        self.assertEqual(container['resources']['requests'], {'cpu': '200m', 'memory': '384Mi'})
        self.assertEqual(container['command'], ['/bin/server'])
        self.assertEqual(container['args'], ['--name=ilm-test'])
        self.assertNotIn('startupProbe', container)
        self.assertEqual(container['readinessProbe']['tcpSocket'], {'port': 'http'})
        self.assertEqual(container['readinessProbe']['periodSeconds'], 7)
        self.assertNotIn('httpGet', container['readinessProbe'])
        self.assertEqual(pod['volumes'][0]['emptyDir'], {'sizeLimit': '64Mi', 'medium': 'Memory'})

    def test_existing_service_account_is_referenced_without_creating_one(self):
        values = configured_values()
        values['fullnameOverride'] = 'shared-mcp'
        values['serviceAccount'] = {'create': False, 'name': 'existing-account'}
        objects = render('mcp-server', values)
        self.assertNotIn('ServiceAccount', [o['kind'] for o in objects])
        deployment = next(o for o in objects if o['kind'] == 'Deployment')
        self.assertEqual(deployment['metadata']['name'], 'shared-mcp')
        self.assertEqual(deployment['spec']['template']['spec']['serviceAccountName'], 'existing-account')

    def test_local_and_global_customizations_use_library_template_rendering(self):
        values = configured_values()
        values['global'] = {
            'additionalEnv': {'variables': [{'name': 'GLOBAL_LABEL', 'value': '{{ .Release.Name }}'}],
                              'configMaps': ['global-config'], 'secrets': ['global-secret']},
            'additionalVolumes': [{'name': 'global-data', 'configMap': {'name': '{{ .Release.Name }}-data'}}],
            'additionalVolumeMounts': [{'name': 'global-data', 'mountPath': '/etc/global', 'readOnly': True}],
            'initContainers': [{'name': 'global-init', 'image': 'example.invalid/init@sha256:' + 'b' * 64,
                                'args': ['{{ .Release.Name }}']}],
        }
        values['additionalEnv'] = {
            'variables': [{'name': 'LOCAL_SECRET', 'valueFrom': {'secretKeyRef': {'name': 'extension', 'key': 'key'}}}],
            'configMaps': ['local-config'], 'secrets': ['local-secret'],
        }
        values['additionalVolumes'] = [{'name': 'local-data', 'emptyDir': {'sizeLimit': '1Mi'}}]
        values['additionalVolumeMounts'] = [{'name': 'local-data', 'mountPath': '/var/local'}]
        values['initContainers'] = [{'name': 'local-init', 'image': 'example.invalid/init@sha256:' + 'c' * 64}]
        pod = next(o for o in render('mcp-server', values)
                   if o['kind'] == 'Deployment')['spec']['template']['spec']
        container = pod['containers'][0]
        env = {entry['name']: entry for entry in container['env']}
        self.assertEqual(env['GLOBAL_LABEL']['value'], 'ilm-test')
        self.assertEqual(env['LOCAL_SECRET']['valueFrom']['secretKeyRef'], {'name': 'extension', 'key': 'key'})
        self.assertEqual(container['envFrom'], [
            {'configMapRef': {'name': 'global-config'}}, {'configMapRef': {'name': 'local-config'}},
            {'secretRef': {'name': 'global-secret'}}, {'secretRef': {'name': 'local-secret'}}])
        self.assertEqual([v['name'] for v in pod['volumes']], ['tmp', 'global-data', 'local-data'])
        self.assertEqual(pod['volumes'][1]['configMap']['name'], 'ilm-test-data')
        self.assertEqual([v['mountPath'] for v in container['volumeMounts']], ['/tmp', '/etc/global', '/var/local'])
        self.assertEqual([v['name'] for v in pod['initContainers']], ['global-init', 'local-init'])
        self.assertEqual(pod['initContainers'][0]['args'], ['ilm-test'])

    def test_customizations_cannot_duplicate_required_environment_or_selectors(self):
        protected_names = ['SPRING_PROFILES_ACTIVE', 'SPRING_AI_MCP_SERVER_PROTOCOL',
                           'SPRING_AI_MCP_SERVER_TYPE', 'ILM_AUTH_METHOD',
                           'PORT', 'MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED',
                           'ILM_API_URL', 'ILM_MCP_ACCESS', 'ILM_MCP_TOOLSETS',
                           'ILM_MCP_SECURITY_ISSUER', 'ILM_MCP_SECURITY_RESOURCE',
                           'ILM_MCP_SECURITY_JWK_SET_URI', 'ILM_MCP_SECURITY_TOKEN_URI',
                           'ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL',
                           'ILM_MCP_SECURITY_CLIENT_ID', 'ILM_MCP_SECURITY_CLIENT_SECRET',
                           'ILM_MCP_SECURITY_API_AUDIENCE', 'ILM_MCP_SECURITY_EXCHANGE_MODE',
                           'ILM_MCP_SECURITY_EXCHANGE_SCOPE', 'ILM_MCP_SECURITY_REQUIRED_SCOPE',
                           'ILM_MCP_SECURITY_AUTHORIZATION_SCOPES', 'ILM_MCP_SECURITY_ALLOWED_ORIGINS',
                           'ILM_MCP_SECURITY_JWS_ALGORITHMS', 'ILM_MCP_SECURITY_ENABLED',
                           'TRUSTED_CERTIFICATES', 'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY']
        for scope in ('local', 'global'):
            for name in protected_names + ['{{ printf "ILM_AUTH_%s" "METHOD" }}']:
                with self.subTest(scope=scope, name=name):
                    values = configured_values()
                    target = values if scope == 'local' else values.setdefault('global', {})
                    target['additionalEnv'] = {'variables': [{'name': name, 'value': 'unsafe'}]}
                    with self.assertRaises(AssertionError):
                        render('mcp-server', values)
        for name in ('app.kubernetes.io/name', 'app.kubernetes.io/instance'):
            with self.subTest(label=name):
                values = configured_values()
                values['podLabels'] = {name: 'other'}
                with self.assertRaises(AssertionError):
                    render('mcp-server', values)

    def test_incomplete_or_unsafe_inputs_fail_rendering(self):
        self.assert_unsafe_inputs_fail(configured_values)

    def test_http_backchannel_requires_the_switch(self):
        switch = 'ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL'
        default_env = deployment_env(render('mcp-server', configured_values()))
        self.assertEqual(default_env[switch], 'false')
        for key, name, url in (
                ('apiUrl', 'ILM_API_URL', 'http://ilm.example.invalid/api'),
                ('jwkSetUri', 'ILM_MCP_SECURITY_JWK_SET_URI', 'http://identity.example.invalid/oauth/jwks'),
                ('tokenUri', 'ILM_MCP_SECURITY_TOKEN_URI', 'http://identity.example.invalid/oauth/token')):
            with self.subTest(key=key):
                values = configured_values()
                owner = values if key == 'apiUrl' else values['security']
                owner[key] = url
                self.assert_render_fails(
                    values, 'security.allowHttpBackchannel must be true for http backchannel URLs')
                values['security']['allowHttpBackchannel'] = True
                env = deployment_env(render('mcp-server', values))
                self.assertEqual(env, {**default_env, name: url, switch: 'true'})

    def test_public_endpoints_stay_https_with_the_switch(self):
        for key in ('issuer', 'resource'):
            with self.subTest(key=key):
                values = configured_values()
                values['security']['allowHttpBackchannel'] = True
                render('mcp-server', values)
                values['security'][key] = values['security'][key].replace('https://', 'http://')
                self.assert_render_fails(values, key)

    def test_service_name_can_be_fixed(self):
        values = configured_values()
        objects = render('mcp-server', values)
        full_name = by_kind(objects, 'Deployment')['metadata']['name']
        self.assertEqual(by_kind(objects, 'Service')['metadata']['name'], full_name)
        values['service'] = {'name': 'mcp-server-service'}
        objects = render('mcp-server', values)
        deployment, service = by_kind(objects, 'Deployment'), by_kind(objects, 'Service')
        self.assertEqual(service['metadata']['name'], 'mcp-server-service')
        self.assertEqual(service['spec']['selector'], deployment['spec']['selector']['matchLabels'])
        self.assertEqual(deployment['metadata']['name'], full_name)
        # Valid names that YAML would read as a boolean or null stay strings.
        for name in ('true', 'null', 'yes'):
            with self.subTest(name=name):
                values['service'] = {'name': name}
                self.assertEqual(by_kind(render('mcp-server', values), 'Service')['metadata']['name'], name)

    def test_network_policy_can_be_disabled(self):
        values = configured_values()
        values['networkPolicy']['enabled'] = False
        remaining = {'Deployment', 'Service', 'ServiceAccount'}
        self.assertEqual({o['kind'] for o in render('mcp-server', values)}, remaining)
        values['networkPolicy'].update(ingress=[], egress=[])
        self.assertEqual({o['kind'] for o in render('mcp-server', values)}, remaining)
        values['networkPolicy']['enabled'] = True
        self.assert_render_fails(values, 'networkPolicy')

    def test_proxy_comes_from_the_globals(self):
        shared = {'httpProxy': 'http://proxy.example.invalid:3128',
                  'httpsProxy': 'http://proxy.example.invalid:3129', 'noProxy': 'core,*.svc'}
        local = {'httpProxy': 'http://local.example.invalid:3128',
                 'httpsProxy': 'http://local.example.invalid:3129', 'noProxy': 'local-core'}

        def env_names(settings):
            return {PROXY_ENV_NAMES[key]: value for key, value in settings.items()}

        for case, global_values, local_values, expected in (
                ('globals', shared, {}, shared),
                ('local values apply when the globals are empty', dict.fromkeys(PROXY_ENV_NAMES, ''), local, local),
                ('a global wins over a local value', shared, local, shared),
                ('each name is chosen on its own', {'noProxy': 'core'}, local, {**local, 'noProxy': 'core'})):
            with self.subTest(case):
                values = {**configured_values(), **local_values, 'global': global_values}
                env = deployment_env(render('mcp-server', values))
                self.assertEqual({name: env.get(name) for name in PROXY_ENV_NAMES.values()}, env_names(expected))

    def test_proxy_unset_renders_nothing(self):
        names = set(PROXY_ENV_NAMES.values())
        for settings in ({}, dict.fromkeys(PROXY_ENV_NAMES, '')):
            with self.subTest(settings=settings):
                values = {**configured_values(), **settings, 'global': settings}
                self.assertEqual(names & set(deployment_env(render('mcp-server', values))), set())
        samples = {'httpProxy': 'http://proxy.example.invalid:3128',
                   'httpsProxy': 'http://proxy.example.invalid:3129', 'noProxy': 'core'}
        for key, value in samples.items():
            with self.subTest(only=key):
                values = {**configured_values(), key: value}
                self.assertEqual(names & set(deployment_env(render('mcp-server', values))), {PROXY_ENV_NAMES[key]})

    def test_proxy_url_must_suit_mcp(self):
        for key in ('httpProxy', 'httpsProxy'):
            for scope in ('global', 'local'):
                for url in ('https://proxy.example.invalid:3129', 'http://proxy.example.invalid'):
                    with self.subTest(key=key, scope=scope, url=url):
                        values = configured_values()
                        scoped(values, scope)[key] = url
                        self.assert_render_fails(
                            values, f'{key} must be an http://host:port URL for the MCP server')
                for url in ('http://user:pass@proxy.example.invalid:3128', 'http://proxy.example.invalid:3128/'):
                    with self.subTest(key=key, scope=scope, url=url):
                        values = configured_values()
                        scoped(values, scope)[key] = url
                        self.assertEqual(deployment_env(render('mcp-server', values))[PROXY_ENV_NAMES[key]], url)

    # What OutboundProxy takes and refuses at startup: the URLs of the server's OutboundProxyTest and the edges of the
    # java.net.URI grammar behind it, namely hosts, IPv6 addresses, ports and the characters of each URL part.
    def test_proxy_url_check_accepts_and_refuses_the_listed_urls(self):
        accepted = (
            'http://proxy.example.invalid:3128',
            'http://user:p%40ss@secure.example.invalid:3129',
            'http://only-user@proxy.example.invalid:3128',
            'http://@proxy.example.invalid:3128',
            'http://127.0.0.1:3128',
            'http://1.2.3.4:65535',
            'http://proxy.example.invalid.:03128',
            'http://[2001:db8::1]:3128',
            'http://[::1]:3128',
            'http://[::ffff:1.2.3.4]:3128',
            'http://[fe80::1%eth0]:3128',
            ' HTTP://proxy.example.invalid:3128/\n',
            'http://proxy.example.invalid:3128/path?x=1#y',
            'http://proxy.example.invalid:3128?x=1',
        )
        refused = (
            'https://proxy.example.invalid:443',
            'proxy-without-scheme',
            'proxy.example.invalid:3128',
            'http://proxy.example.invalid',
            'http://proxy.example.invalid:0',
            'http://proxy.example.invalid:65536',
            'http://proxy.example.invalid:99999',
            'http://proxy.example.invalid:3128x',
            'http://',
            'http://:3128',
            'http://under_score:3128',
            'http://-proxy.example.invalid:3128',
            'http://proxy.example.invalid.1:3128',
            'http://1.2.3.256:3128',
            'http://proxy.example.invalid:port',
            'http://[::1]',
            'http://[:::1]:3128',
            'http://[1:2:3]:3128',
            'http://[::1%a-b]:3128',
            'http://proxy.example.invalid:3128/a b',
            'http://user:must-not-leak@proxy.example.invalid',
            'https://user:must-not-leak@proxy.example.invalid:443',
            'http://user:p%ss-must-not-leak@proxy.example.invalid:3128',
            'http://user:p%4@proxy.example.invalid:3128',
            'http://user:p ss-must-not-leak@proxy.example.invalid:3128',
            'http://user:p@ss-must-not-leak@proxy.example.invalid:3128',
        )
        for url in accepted:
            with self.subTest(accepted=url):
                values = {**configured_values(), 'httpProxy': url}
                self.assertEqual(deployment_env(render('mcp-server', values))['HTTP_PROXY'], url)
        for url in refused:
            with self.subTest(refused=url):
                values = {**configured_values(), 'global': {'httpsProxy': url}}
                with self.assertRaises(AssertionError) as raised:
                    render('mcp-server', values)
                self.assertIn('httpsProxy must be an http://host:port URL for the MCP server', str(raised.exception))
                self.assertNotIn('must-not-leak', str(raised.exception))

    def test_proxy_env_appends_extra_hosts_to_no_proxy_while_a_proxy_is_set(self):
        with tempfile.TemporaryDirectory(prefix='ilm-mcp-proxy-env-') as temp:
            chart = Path(temp) / 'mcp-server'
            shutil.copytree(ROOT / 'deploy/charts/mcp-server', chart)
            (chart / 'templates/proxy-env-probe.yaml').write_text(
                'apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: probe\ndata:\n'
                '  env: {{ include "mcp-server.proxyEnv" (dict "context" . "noProxyHosts" .Values.probeHosts) | quote }}\n')

            def no_proxy(hosts, **settings):
                values = {**configured_values(), 'probeHosts': hosts, **settings}
                probe = by_kind(render_path(chart, values), 'ConfigMap')
                env = yaml.safe_load(probe['data']['env']) or []
                return {entry['name']: entry['value'] for entry in env}.get('NO_PROXY')

            proxy = {'httpProxy': 'http://proxy.example.invalid:3128'}
            self.assertEqual(no_proxy(['core', 'kc.idp.svc'], noProxy='localhost', **proxy),
                             'localhost,core,kc.idp.svc')
            self.assertEqual(no_proxy(['core', 'kc.idp.svc'], **proxy), 'core,kc.idp.svc')
            self.assertEqual(no_proxy(['core'], httpsProxy='http://proxy.example.invalid:3129'), 'core')
            # Listed hosts are not repeated, whatever their case, separator or position in the extra hosts.
            self.assertEqual(no_proxy(['Core', 'new', 'NEW'], noProxy='core | other', **proxy), 'core | other,new')
            self.assertEqual(no_proxy(['core'], noProxy='core', **proxy), 'core')
            self.assertEqual(no_proxy([], noProxy='localhost', **proxy), 'localhost')
            # Without a proxy the extra hosts add nothing, and NO_PROXY is only what was set.
            self.assertIsNone(no_proxy(['core']))
            self.assertEqual(no_proxy(['core'], noProxy='localhost'), 'localhost')

    def test_trusted_certificates_follow_the_shared_secrets(self):
        def reference(name):
            return [{'name': 'TRUSTED_CERTIFICATES',
                     'valueFrom': {'secretKeyRef': {'name': name, 'key': 'ca.crt'}}}]

        values = configured_values()
        values['global'] = {'trusted': {'certificates': SYNTHETIC_CA}}
        objects = render('mcp-server', values)
        self.assertEqual(trusted_certificates_entries(objects), reference('trusted-certificates'))
        # The umbrella renders the shared Secret.
        self.assertNotIn('Secret', [o['kind'] for o in objects])

        values = configured_values()
        values['trusted'] = {'certificates': SYNTHETIC_CA}
        objects = render('mcp-server', values)
        self.assertEqual(trusted_certificates_entries(objects), reference('trusted-certificates-mcp-server'))
        secret = by_kind(objects, 'Secret')
        self.assertEqual(secret['metadata']['name'], 'trusted-certificates-mcp-server')
        self.assertEqual(secret['data'], {'ca.crt': base64.b64encode(SYNTHETIC_CA.encode()).decode()})

        values['global'] = {'trusted': {'certificates': OTHER_SYNTHETIC_CA}}
        self.assertEqual(trusted_certificates_entries(render('mcp-server', values)), reference('trusted-certificates'))

        for values in (configured_values(),
                       {**configured_values(), 'trusted': {'certificates': ''},
                        'global': {'trusted': {'certificates': ''}}}):
            objects = render('mcp-server', values)
            self.assertEqual(trusted_certificates_entries(objects), [])
            self.assertNotIn('Secret', [o['kind'] for o in objects])

    def test_trust_change_rolls_the_pod(self):
        checksums = {'checksum/trusted-certificates-local', 'checksum/trusted-certificates-global'}

        def annotations(scope=None, certificates=SYNTHETIC_CA, **extra):
            values = {**configured_values(), **extra}
            if scope:
                scoped(values, scope)['trusted'] = {'certificates': certificates}
            return pod_annotations(render('mcp-server', values))

        self.assertEqual(checksums & set(annotations()), set())
        for scope in ('local', 'global'):
            key = f'checksum/trusted-certificates-{scope}'
            with self.subTest(scope=scope):
                current = annotations(scope)
                self.assertEqual(checksums & set(current), {key})
                self.assertEqual(annotations(scope)[key], current[key])
                self.assertNotEqual(annotations(scope, OTHER_SYNTHETIC_CA)[key], current[key])
        values = {**configured_values(), 'trusted': {'certificates': SYNTHETIC_CA},
                  'global': {'trusted': {'certificates': SYNTHETIC_CA}},
                  'podAnnotations': {'example.invalid/review': 'kept'}}
        merged = pod_annotations(render('mcp-server', values))
        self.assertEqual(checksums & set(merged), checksums)
        self.assertEqual(merged['example.invalid/review'], 'kept')

    # Null deletes a default map when Helm coalesces the values, so the schema has to require both maps.
    def test_null_trust_values_fail_the_schema(self):
        for patch in ({'trusted': None}, {'global': {'trusted': None}}):
            with self.subTest(patch=patch):
                nulled = {**configured_values(), **patch}
                with self.assertRaises(AssertionError) as raised:
                    render('mcp-server', nulled)
                self.assertIn("values don't meet the specifications of the schema", str(raised.exception))
                self.assertIn('trusted', str(raised.exception))

    def test_null_umbrella_values_fail_the_schema(self):
        for path in ('keycloak', 'keycloak.enabled', 'mcp', 'mcp.enabled', 'mcp.identity', 'mcp.clientSecretRef'):
            with self.subTest(path=path):
                values = umbrella_values('managed')
                *parents, leaf = path.split('.')
                target = values['global']
                for key in parents:
                    target = target[key]
                target[leaf] = None
                with self.assertRaises(AssertionError) as raised:
                    render('mcp-server', values)
                self.assertIn("values don't meet the specifications of the schema", str(raised.exception))
                self.assertIn(leaf, str(raised.exception))

    def test_pod_annotations_that_render_to_invalid_yaml_fail_the_render(self):
        values = {**configured_values(), 'x': "it's",
                  'podAnnotations': {'foo': '{{ .Values.x }}', 'keep': 'yes'}}
        self.assert_render_fails(values, 'YAML parse error')

    def test_pod_annotations_render_as_given(self):
        # No annotation name is special, and without any annotation the pod has no annotations key.
        values = {**configured_values(), 'podAnnotations': {'Error': 'example', 'example.invalid/review': 'kept'}}
        self.assertEqual(pod_annotations(render('mcp-server', values)),
                         {'Error': 'example', 'example.invalid/review': 'kept'})
        pod = by_kind(render('mcp-server', configured_values()), 'Deployment')['spec']['template']
        self.assertNotIn('annotations', pod['metadata'])

    def test_umbrella_managed_mode_derives_everything(self):
        keycloak = 'http://keycloak-internal-service:8080/kc/realms/ILM/protocol/openid-connect'
        objects = self.assert_env(umbrella_values(), {
            'ILM_API_URL': 'http://core-service:8080/api',
            'ILM_MCP_SECURITY_RESOURCE': 'https://ilm.example.invalid/mcp',
            'ILM_MCP_SECURITY_ISSUER': 'https://ilm.example.invalid/kc/realms/ILM',
            'ILM_MCP_SECURITY_JWK_SET_URI': f'{keycloak}/certs',
            'ILM_MCP_SECURITY_TOKEN_URI': f'{keycloak}/token',
            'ILM_MCP_SECURITY_CLIENT_ID': 'ilm-mcp',
            'ILM_MCP_SECURITY_EXCHANGE_MODE': 'delegation',
            'ILM_MCP_SECURITY_EXCHANGE_SCOPE': 'openid ilm-mcp-upstream',
            'ILM_MCP_SECURITY_AUTHORIZATION_SCOPES': 'openid,delegation:client:ilm-mcp',
            'ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL': 'true',
        })
        self.assertEqual(client_secret_ref(objects), {'name': 'mcp-server-secret', 'key': 'client-secret'})

    def test_umbrella_mode_uses_the_global_secret_reference(self):
        values = umbrella_values()
        values['global']['mcp']['clientSecretRef'] = {'name': 'mcp-oauth', 'key': 'secret'}
        self.assertEqual(client_secret_ref(render('mcp-server', values)), {'name': 'mcp-oauth', 'key': 'secret'})

    def test_umbrella_mode_host_falls_back_to_hostName(self):
        values = umbrella_values(hostName='ilm.example.invalid')
        values['global']['hostName'] = ''
        self.assert_env(values, {'ILM_MCP_SECURITY_RESOURCE': 'https://ilm.example.invalid/mcp',
                                 'ILM_MCP_SECURITY_ISSUER': 'https://ilm.example.invalid/kc/realms/ILM'})
        values['global']['hostName'] = 'global.example.invalid'
        self.assert_env(values, {'ILM_MCP_SECURITY_RESOURCE': 'https://global.example.invalid/mcp'})

    def test_umbrella_mode_requires_the_host_name(self):
        for mode, values in (('managed', umbrella_values()), ('external', external_values())):
            with self.subTest(mode=mode):
                values['global']['hostName'] = ''
                self.assert_render_fails(values, 'global.hostName is required when global.mcp.enabled is true')

    def test_managed_mode_needs_the_bundled_keycloak(self):
        values = umbrella_values('managed')
        values['global']['keycloak']['enabled'] = False
        self.assert_render_fails(
            values, 'global.mcp.identity.mode managed needs global.keycloak.enabled; use external for another provider')

    def test_managed_mode_owns_the_identity_settings(self):
        # Even the values the chart derives fail, because the ILM chart's realm import cannot see them.
        for key, value in (('resource', 'https://ilm.example.invalid/mcp'),
                           ('issuer', 'https://ilm.example.invalid/kc/realms/ILM'),
                           ('clientId', 'ilm-mcp'),
                           ('clientSecretRef.name', 'mcp-server-secret'),
                           ('exchangeMode', 'delegation'),
                           ('exchangeScope', 'openid ilm-mcp-upstream'),
                           ('authorizationScopes', ['openid', 'delegation:client:ilm-mcp'])):
            with self.subTest(key=key):
                setting = {'clientSecretRef': {'name': value}} if key == 'clientSecretRef.name' else {key: value}
                self.assert_render_fails(umbrella_values(security=setting),
                                         f'security.{key} cannot be set with global.mcp.identity.mode managed')

    def test_managed_mode_in_cluster_urls_can_be_set(self):
        keycloak = 'http://keycloak-internal-service:8081/kc/realms/ILM/protocol/openid-connect'
        values = umbrella_values(apiUrl='http://core-service:8081/api',
                                 security={'jwkSetUri': f'{keycloak}/certs', 'tokenUri': f'{keycloak}/token'})
        self.assert_env(values, {'ILM_API_URL': 'http://core-service:8081/api',
                                 'ILM_MCP_SECURITY_JWK_SET_URI': f'{keycloak}/certs',
                                 'ILM_MCP_SECURITY_TOKEN_URI': f'{keycloak}/token'})

    def test_managed_client_secret_rolls_the_pod(self):
        def checksum(**mcp):
            values = umbrella_values()
            values['global']['mcp'].update(mcp)
            return pod_annotations(render('mcp-server', values)).get('checksum/client-secret')

        current = checksum(clientSecret='SYNTHETIC_CLIENT_SECRET')
        self.assertTrue(current)
        self.assertEqual(checksum(clientSecret='SYNTHETIC_CLIENT_SECRET'), current)
        self.assertNotEqual(checksum(clientSecret='OTHER_SYNTHETIC_CLIENT_SECRET'), current)
        self.assertIsNone(checksum(clientSecretRef={'name': 'mcp-oauth', 'key': 'secret'}))
        self.assertIsNone(checksum())

    def test_external_mode_requires_the_provider_settings(self):
        objects = self.assert_env(external_values(), {
            'ILM_API_URL': 'http://core-service:8080/api',
            'ILM_MCP_SECURITY_RESOURCE': 'https://ilm.example.invalid/mcp',
            'ILM_MCP_SECURITY_ISSUER': 'https://identity.example.invalid/tenant',
            'ILM_MCP_SECURITY_JWK_SET_URI': 'https://identity.example.invalid/oauth/jwks',
            'ILM_MCP_SECURITY_TOKEN_URI': 'https://identity.example.invalid/oauth/token',
            'ILM_MCP_SECURITY_CLIENT_ID': 'mcp-exchange',
            'ILM_MCP_SECURITY_EXCHANGE_MODE': 'standard',
            'ILM_MCP_SECURITY_EXCHANGE_SCOPE': '',
            'ILM_MCP_SECURITY_AUTHORIZATION_SCOPES': '',
            'ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL': 'true',
        })
        self.assertEqual(client_secret_ref(objects), {'name': 'mcp-oauth', 'key': 'client-secret'})
        for key in EXTERNAL_PROVIDER:
            name = 'clientSecretRef.name' if key == 'clientSecretRef' else key
            with self.subTest(missing=name):
                values = external_values()
                del values['security'][key]
                self.assert_render_fails(values, f'security.{name} is required with global.mcp.identity.mode external')
        values = external_values(resource='https://mcp.example.invalid/mcp', exchangeScope='ilm',
                                 authorizationScopes=['openid'])
        values['apiUrl'] = 'https://ilm.example.invalid/api'
        self.assert_env(values, {'ILM_API_URL': 'https://ilm.example.invalid/api',
                                 'ILM_MCP_SECURITY_RESOURCE': 'https://mcp.example.invalid/mcp',
                                 'ILM_MCP_SECURITY_EXCHANGE_SCOPE': 'ilm',
                                 'ILM_MCP_SECURITY_AUTHORIZATION_SCOPES': 'openid'})

    def test_external_mode_url_rules(self):
        for key, name in (('jwkSetUri', 'ILM_MCP_SECURITY_JWK_SET_URI'), ('tokenUri', 'ILM_MCP_SECURITY_TOKEN_URI')):
            with self.subTest(key=key):
                self.assert_render_fails(external_values(**{key: 'http://idp.example.invalid/realms/x/endpoint'}),
                                         f'security.{key} must be https, or http to a <service>.<namespace>.svc host')
                for url in ('http://kc.idp.svc:8080/realms/x/endpoint',
                            'http://kc.idp.svc.cluster.local/realms/x/endpoint',
                            'https://identity.example.invalid/oauth/endpoint?tenant=x',
                            'http://kc.idp.svc:8080/realms/x/endpoint?tenant=x'):
                    self.assert_env(external_values(**{key: url}), {name: url})
                for url in ('https://identity.example.invalid/oauth/endpoint#x',
                            'http://kc.idp.svc:8080/realms/x/endpoint#x'):
                    self.assert_render_fails(external_values(**{key: url}), key)
        for issuer in ('https://user@identity.example.invalid/tenant',
                       'https://identity.example.invalid/tenant?realm=x',
                       'https://identity.example.invalid/tenant#x'):
            with self.subTest(issuer=issuer):
                self.assert_render_fails(external_values(issuer=issuer), 'issuer')

    def test_external_mode_refuses_the_bundled_keycloak(self):
        for issuer in ('https://ilm.example.invalid/kc/realms/x',
                       'https://ILM.example.invalid/kc/realms/x',
                       'https://ilm.example.invalid:443/kc/realms/x'):
            with self.subTest(issuer=issuer):
                values = external_values(issuer=issuer)
                self.assert_render_fails(values, 'use global.mcp.identity.mode managed for the bundled Keycloak')
                # Without the bundled Keycloak, an issuer on the ILM host belongs to another provider.
                values['global']['keycloak']['enabled'] = False
                self.assert_env(values, {'ILM_MCP_SECURITY_ISSUER': issuer})

    def test_umbrella_no_proxy_lists_mcp_hosts(self):
        def no_proxy(values, **proxy):
            values['global'].update(proxy)
            return deployment_env(render('mcp-server', values)).get('NO_PROXY')

        proxy = {'httpProxy': 'http://proxy.example.invalid:3128', 'noProxy': 'localhost'}
        in_cluster_keys = 'http://kc.idp.svc:8080/realms/x/protocol/openid-connect/certs'
        self.assertEqual(no_proxy(umbrella_values(), **proxy), 'localhost,core-service,keycloak-internal-service')
        self.assertEqual(no_proxy(external_values(jwkSetUri=in_cluster_keys), **proxy),
                         'localhost,core-service,kc.idp.svc')
        self.assertEqual(no_proxy(external_values(), **proxy), 'localhost,core-service')
        self.assertIsNone(no_proxy(umbrella_values()))
        self.assertEqual(no_proxy(umbrella_values(), **{**proxy, 'noProxy': 'keycloak-internal-service,core-service'}),
                         'keycloak-internal-service,core-service')

    def test_standalone_exchange_mode_defaults_to_standard(self):
        values = configured_values()
        del values['security']['exchangeMode']
        self.assert_env(values, {'ILM_MCP_SECURITY_EXCHANGE_MODE': 'standard'})

    def test_standalone_mode_ignores_ilm_globals(self):
        def with_ilm_globals():
            return {**configured_values(), 'global': copy.deepcopy(ILM_GLOBALS)}

        values = with_ilm_globals()
        del values['security']['issuer']
        self.assert_render_fails(values, 'security.issuer is required')
        self.assert_unsafe_inputs_fail(with_ilm_globals)

    @unittest.skipUnless(os.environ.get('MCP_CHART_BASELINE'),
                         'set MCP_CHART_BASELINE to the git ref to compare the standalone render with')
    def test_standalone_render_matches_baseline(self):
        self.maxDiff = None
        default_exchange_mode = configured_values()
        del default_exchange_mode['security']['exchangeMode']
        for case, values in (('configured', configured_values()), ('default exchangeMode', default_exchange_mode)):
            with self.subTest(case):
                baseline = render_ref(os.environ['MCP_CHART_BASELINE'], values)
                current = render('mcp-server', values)
                for obj in baseline + current:
                    obj['metadata']['labels'].pop('helm.sh/chart')
                mcp_container(current)['env'].remove(
                    {'name': 'ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL', 'value': 'false'})
                self.assertEqual(current, baseline)


if __name__ == '__main__':
    unittest.main()
