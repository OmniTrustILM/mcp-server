"""Shared MCP deployment boundaries, rendered without cluster or registry access."""
import copy
import json
from pathlib import Path
import shutil
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


class SharedMcpChartTests(unittest.TestCase):
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
            (parent / 'Chart.yaml').write_text(yaml.safe_dump({
                'apiVersion': 'v2', 'name': 'ilm', 'version': '0.1.0',
                'dependencies': [{'name': 'mcp-server', 'alias': 'mcpServer',
                                  'version': '0.1.0-develop', 'condition': 'mcpServer.enabled'}],
            }))
            shutil.copytree(ROOT / 'deploy/charts/mcp-server', parent / 'charts/mcp-server')
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
                           'ILM_MCP_SECURITY_CLIENT_ID', 'ILM_MCP_SECURITY_CLIENT_SECRET',
                           'ILM_MCP_SECURITY_API_AUDIENCE', 'ILM_MCP_SECURITY_EXCHANGE_MODE',
                           'ILM_MCP_SECURITY_EXCHANGE_SCOPE', 'ILM_MCP_SECURITY_REQUIRED_SCOPE',
                           'ILM_MCP_SECURITY_AUTHORIZATION_SCOPES', 'ILM_MCP_SECURITY_ALLOWED_ORIGINS',
                           'ILM_MCP_SECURITY_JWS_ALGORITHMS', 'ILM_MCP_SECURITY_ENABLED']
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
        cases = (
            ('volumes', {'ephemeral': {'sizeLimit': ''}}),
            ('image', {'digest': 'unpublished'}),
            ('apiUrl', 'http://ilm.example.invalid/api'),
            ('apiUrl', None),
            ('security', {'issuer': None}),
            ('security', {'clientSecretRef': None}),
            ('networkPolicy', {'ingress': None}),
            ('security', {'issuer': 'http://identity.example.invalid'}),
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
        )
        for key, patch in cases:
            with self.subTest(key=key, patch=patch):
                values = copy.deepcopy(configured_values())
                if isinstance(patch, dict) and key in values:
                    values[key].update(patch)
                else:
                    values[key] = patch
                with self.assertRaises(AssertionError):
                    render('mcp-server', values)


if __name__ == '__main__':
    unittest.main()
