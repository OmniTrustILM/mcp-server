"""Offline checks for first publication, dispatch and Helm 4 output handling."""
import importlib.util
import base64
from contextlib import contextmanager
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from threading import Thread
import unittest
from urllib.request import HTTPSHandler, build_opener

import yaml


def script(name):
    path = Path(__file__).resolve().parents[1] / 'scripts' / f'{name}.py'
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ChartPublicationTests(unittest.TestCase):
    def test_immutable_version_package_keeps_kubernetes_labels_valid(self):
        root = Path(__file__).resolve().parents[2]
        helm = os.environ.get('HELM_BIN', 'helm')
        with tempfile.TemporaryDirectory(prefix='ilm-mcp-chart-package-') as temp:
            version = '0.1.0-develop.' + 'a' * 40
            subprocess.run([helm, 'package', str(root / 'deploy/charts/mcp-server'),
                            '--version', version, '--destination', temp],
                           check=True, capture_output=True, text=True)
            rendered = subprocess.run([helm, 'template', 'mcp-smoke',
                                       str(Path(temp) / f'mcp-server-{version}.tgz'),
                                       '--values', str(root / 'deploy/charts/mcp-server/ci/default-values.yaml')],
                                      check=True, capture_output=True, text=True)
        for obj in yaml.safe_load_all(rendered.stdout):
            self.assertLessEqual(len(obj['metadata']['labels']['helm.sh/chart']), 63)

    def test_development_version_uses_entire_commit(self):
        resolve = script('chart_version').resolve
        self.assertEqual(resolve('0.1.0-develop', 'branch', 'main', 'a' * 40),
                         '0.1.0-develop.' + 'a' * 40)
        for source, ref_type, ref_name, sha in [('0.1.0-develop', 'branch', 'feature', 'a' * 40),
                                               ('0.1.0-develop', 'branch', 'main', 'a' * 7),
                                               ('0.1.0', 'branch', 'main', 'a' * 40)]:
            with self.assertRaises(ValueError):
                resolve(source, ref_type, ref_name, sha)

    def test_version_cli_resolves_repository_chart_from_another_directory(self):
        root = Path(__file__).resolve().parents[2]
        source = yaml.safe_load((root / 'deploy/charts/mcp-server/Chart.yaml').read_text())['version']
        with tempfile.TemporaryDirectory(prefix='ilm-mcp-version-cwd-') as cwd:
            result = subprocess.run([
                sys.executable, str(root / 'deploy/scripts/chart_version.py'),
                '--ref-type', 'branch', '--ref-name', 'main', '--sha', 'a' * 40,
            ], cwd=cwd, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), source + '.' + 'a' * 40)

    def test_version_cli_rejects_invalid_publication_context(self):
        root = Path(__file__).resolve().parents[2]
        for ref_name, sha in [('feature', 'a' * 40), ('main', 'a' * 7)]:
            with self.subTest(ref_name=ref_name, sha=sha):
                result = subprocess.run([
                    sys.executable, str(root / 'deploy/scripts/chart_version.py'),
                    '--ref-type', 'branch', '--ref-name', ref_name, '--sha', sha,
                ], capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, '')

    def test_digest_cli_reads_helm_output_from_stdin(self):
        root = Path(__file__).resolve().parents[2]
        result = subprocess.run([
            sys.executable, str(root / 'deploy/scripts/push_digest.py'),
        ], input='Pushed: registry.invalid/chart:0.1.0\nDigest: sha256:' + 'a' * 64 + '\n',
            capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), 'sha256:' + 'a' * 64)

    def test_release_version_must_match_stable_tag(self):
        resolve = script('chart_version').resolve
        self.assertEqual(resolve('0.1.0', 'tag', 'v0.1.0', 'a' * 40), '0.1.0')
        self.assertEqual(resolve('0.1.0', 'tag', '0.1.0', 'a' * 40), '0.1.0')
        for source, ref in [('0.1.0-develop', 'v0.1.0'), ('0.1.0', 'v0.2.0'),
                            ('0.1.0', 'release-0.1.0')]:
            with self.assertRaises(ValueError):
                resolve(source, 'tag', ref, 'a' * 40)

    def test_helm_digest_does_not_depend_on_line_number(self):
        parse = script('push_digest').parse
        digest = 'sha256:' + 'a' * 64
        output = f'WARNING: synthetic warning\nPushed: registry.invalid/ilm-helm/mcp-server:0.1.0\nDigest: {digest}\n'
        self.assertEqual(parse(output), digest)
        for invalid in ['', 'Digest: sha256:broken', output + 'Digest: sha256:' + 'b' * 64]:
            with self.assertRaises(ValueError):
                parse(invalid)


SIGNED_DIGEST = 'sha256:' + 'a' * 64
OLD_DIGEST = 'sha256:' + 'b' * 64
ARTIFACTS = '/api/v2.0/projects/ilm-helm/repositories/mcp-server/artifacts/'
TARGET_PATH = ARTIFACTS + 'sha256%3A' + 'a' * 64
ALIAS_PATH = ARTIFACTS + '0.0.0'
DELETE_PATH = ARTIFACTS + 'sha256%3A' + 'b' * 64 + '/tags/0.0.0'
DEVELOP_VERSION = '0.1.0-develop.' + 'c' * 40


@contextmanager
def harbor_fixture(module, responses):
    """Exercise the real HTTP client against an offline, scripted Harbor server."""
    pending = list(responses)
    observed = []

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.respond()

        def do_DELETE(self):
            self.respond()

        def do_POST(self):
            self.respond()

        def respond(self):
            payload = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            observed.append((self.command, self.path, payload, self.headers.get('Authorization')))
            if not pending:
                self.send_error(500)
                return
            status, body = pending.pop(0)
            if status is None:
                self.wfile.write(body)
                return
            self.send_response(status)
            if 300 <= status < 400:
                self.send_header('Location', '/must-not-follow')
            self.end_headers()
            self.wfile.write(body if isinstance(body, bytes) else json.dumps(body).encode())

        def log_message(self, *_args):
            # Requests are asserted through observed; keep synthetic HTTP logs quiet.
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    class OfflineHTTPS(HTTPSHandler):
        def https_open(self, request):
            # Production URLs stay fixed; only the fixture's connection is local.
            if request.host != 'hub.omnitrustregistry.com':
                raise AssertionError(f'Unexpected credential destination: {request.host}')
            return self.do_open(lambda _host, **_kwargs: HTTPConnection(*server.server_address), request)

    try:
        client = module.HarborClient('synthetic-user', 'synthetic-password',
                                     opener=build_opener(module.NoRedirect(), OfflineHTTPS()))
        yield client, observed, pending
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class DevelopmentAliasTests(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).resolve().parents[1] / 'scripts/development_alias.py'
        self.assertTrue(path.is_file(), 'The development alias publisher is not implemented')
        self.module = script('development_alias')

    def update(self, client, **overrides):
        arguments = {'event_name': 'push', 'ref_type': 'branch', 'ref_name': 'main',
                     'version': DEVELOP_VERSION, 'digest': SIGNED_DIGEST, 'client': client,
                     'sha': 'c' * 40, 'current_main': lambda: 'c' * 40}
        arguments.update(overrides)
        return self.module.update_alias(**arguments)

    def test_first_publication_creates_alias_for_the_signed_digest(self):
        responses = [(200, {'digest': SIGNED_DIGEST}), (404, {}), (201, {}),
                     (200, {'digest': SIGNED_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, pending):
            self.update(client)
        self.assertEqual([(method, path) for method, path, *_ in observed], [
            ('GET', TARGET_PATH), ('GET', ALIAS_PATH), ('POST', TARGET_PATH + '/tags'),
            ('GET', ALIAS_PATH)])
        self.assertEqual(json.loads(observed[2][2]), {'name': '0.0.0'})
        authorization = 'Basic ' + base64.b64encode(b'synthetic-user:synthetic-password').decode()
        self.assertTrue(all(request[3] == authorization for request in observed))
        self.assertEqual(pending, [])

    def test_existing_alias_moves_only_the_development_tag(self):
        responses = [(200, {'digest': SIGNED_DIGEST}), (200, {'digest': OLD_DIGEST}),
                     (200, {}), (201, {}), (200, {'digest': SIGNED_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, pending):
            self.update(client)
        self.assertEqual([(method, path) for method, path, *_ in observed], [
            ('GET', TARGET_PATH), ('GET', ALIAS_PATH), ('DELETE', DELETE_PATH),
            ('POST', TARGET_PATH + '/tags'), ('GET', ALIAS_PATH)])
        self.assertEqual(pending, [])

    def test_matching_alias_is_idempotent(self):
        responses = [(200, {'digest': SIGNED_DIGEST}), (200, {'digest': SIGNED_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, pending):
            self.update(client)
        self.assertEqual([(method, path) for method, path, *_ in observed], [
            ('GET', TARGET_PATH), ('GET', ALIAS_PATH)])
        self.assertEqual(pending, [])

    def test_manual_main_publication_updates_the_alias(self):
        responses = [(200, {'digest': SIGNED_DIGEST}), (404, {}), (201, {}),
                     (200, {'digest': SIGNED_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, pending):
            self.update(client, event_name='workflow_dispatch')
        self.assertEqual([request[0] for request in observed], ['GET', 'GET', 'POST', 'GET'])
        self.assertEqual(pending, [])

    def test_stale_main_publication_skips_alias_without_writes(self):
        responses = [(200, {'digest': SIGNED_DIGEST}), (200, {'digest': OLD_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, _):
            updated = self.update(client, current_main=lambda: 'd' * 40)
        self.assertFalse(updated)
        self.assertEqual([request[0] for request in observed], ['GET', 'GET'])

    def test_failed_main_lookup_cannot_change_alias(self):
        def failed_lookup():
            raise self.module.PublicationError('Main ref lookup failed')

        responses = [(200, {'digest': SIGNED_DIGEST}), (200, {'digest': OLD_DIGEST})]
        with harbor_fixture(self.module, responses) as (client, observed, _), \
                self.assertRaises(self.module.PublicationError):
            self.update(client, current_main=failed_lookup)
        self.assertEqual([request[0] for request in observed], ['GET', 'GET'])

    def test_current_main_lookup_requires_one_exact_sha_and_ref(self):
        self.assertTrue(hasattr(self.module, 'read_main_sha'), 'Fresh main lookup is not implemented')
        good = subprocess.CompletedProcess([], 0, 'c' * 40 + '\trefs/heads/main\n', '')
        self.assertEqual(self.module.read_main_sha(run=lambda *_args, **_kwargs: good), 'c' * 40)
        invalid = [subprocess.CompletedProcess([], 1, '', 'synthetic-sensitive-error'),
                   subprocess.CompletedProcess([], 0, 'c' * 7 + '\trefs/heads/main\n', ''),
                   subprocess.CompletedProcess([], 0, 'c' * 40 + '\trefs/heads/feature\n', ''),
                   subprocess.CompletedProcess([], 0, good.stdout + good.stdout, '')]
        for response in invalid:
            with self.subTest(response=response), self.assertRaises(self.module.PublicationError) as failure:
                self.module.read_main_sha(run=lambda *_args, response=response, **_kwargs: response)
            self.assertNotIn('synthetic-sensitive-error', str(failure.exception))

    def test_missing_or_failed_target_lookup_cannot_change_alias(self):
        for status in [401, 403, 404, 500]:
            with self.subTest(status=status), harbor_fixture(self.module, [(status, {})]) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([(method, path) for method, path, *_ in observed], [('GET', TARGET_PATH)])

    def test_only_alias_lookup_404_means_absent(self):
        for status in [401, 403, 429, 500]:
            responses = [(200, {'digest': SIGNED_DIGEST}), (status, {})]
            with self.subTest(status=status), harbor_fixture(self.module, responses) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([request[0] for request in observed], ['GET', 'GET'])

    def test_failed_delete_does_not_attempt_create(self):
        for status in [403, 404, 500]:
            responses = [(200, {'digest': SIGNED_DIGEST}), (200, {'digest': OLD_DIGEST}), (status, {})]
            with self.subTest(status=status), harbor_fixture(self.module, responses) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([request[0] for request in observed], ['GET', 'GET', 'DELETE'])

    def test_failed_create_cannot_report_success(self):
        for status in [403, 409, 500]:
            responses = [(200, {'digest': SIGNED_DIGEST}), (404, {}), (status, {})]
            with self.subTest(status=status), harbor_fixture(self.module, responses) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([request[0] for request in observed], ['GET', 'GET', 'POST'])

    def test_failed_or_wrong_digest_verification_cannot_report_success(self):
        for final in [(404, {}), (500, {}), (200, {'digest': OLD_DIGEST})]:
            responses = [(200, {'digest': SIGNED_DIGEST}), (404, {}), (201, {}), final]
            with self.subTest(final=final), harbor_fixture(self.module, responses) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([request[0] for request in observed], ['GET', 'GET', 'POST', 'GET'])

    def test_wrong_target_digest_cannot_change_alias(self):
        with harbor_fixture(self.module, [(200, {'digest': OLD_DIGEST})]) as (client, observed, _):
            with self.assertRaises(self.module.PublicationError):
                self.update(client)
            self.assertEqual([request[0] for request in observed], ['GET'])

    def test_invalid_artifact_responses_cannot_change_alias(self):
        for body in [b'not JSON', {}, [], {'digest': '../../other'}, {'digest': 'sha256:broken'}]:
            responses = [(200, {'digest': SIGNED_DIGEST}), (200, body)]
            with self.subTest(body=body), harbor_fixture(self.module, responses) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([request[0] for request in observed], ['GET', 'GET'])

    def test_redirects_are_not_followed(self):
        for status in [301, 302, 307, 308]:
            with self.subTest(status=status), harbor_fixture(self.module, [(status, {})]) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client)
                self.assertEqual([(method, path) for method, path, *_ in observed], [('GET', TARGET_PATH)])

    def test_malformed_http_status_is_sanitized(self):
        with harbor_fixture(self.module, [(None, b'synthetic-sensitive-server-response\n')]) as (client, _, _), \
                self.assertRaises(self.module.PublicationError) as failure:
            client.artifact_digest(SIGNED_DIGEST)
        self.assertNotIn('synthetic-sensitive-server-response', str(failure.exception))

    def test_forbidden_refs_versions_and_digests_make_no_requests(self):
        invalid = [{'event_name': 'pull_request'}, {'ref_type': 'tag'},
                   {'ref_name': 'feature'}, {'version': '0.1.0'}, {'version': '0.0.0'},
                   {'version': '0.1.0-develop.abcdef0'}, {'digest': 'sha256:broken'},
                   {'digest': 'https://registry.invalid'}, {'digest': SIGNED_DIGEST + '/tags'},
                   {'sha': 'd' * 40}]
        for overrides in invalid:
            with self.subTest(overrides=overrides), harbor_fixture(self.module, []) as (client, observed, _):
                with self.assertRaises(self.module.PublicationError):
                    self.update(client, **overrides)
                self.assertEqual(observed, [])

    def test_cli_failure_does_not_log_credentials(self):
        root = Path(__file__).resolve().parents[2]
        result = subprocess.run([
            sys.executable, str(root / 'deploy/scripts/development_alias.py'), '--event-name', 'push',
            '--ref-type', 'branch', '--ref-name', 'feature', '--version', DEVELOP_VERSION,
            '--digest', SIGNED_DIGEST, '--sha', 'c' * 40,
        ], env={**os.environ, 'HELM_USERNAME': 'secret-user', 'HELM_PASSWORD': 'secret-password'},
            capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('secret-user', result.stdout + result.stderr)
        self.assertNotIn('secret-password', result.stdout + result.stderr)


class PublicationWorkflowTests(unittest.TestCase):
    def test_every_main_push_can_refresh_the_development_alias(self):
        root = Path(__file__).resolve().parents[2]
        workflow = yaml.load((root / '.github/workflows/publish_chart.yaml').read_text(), Loader=yaml.BaseLoader)
        push = workflow['on']['push']
        self.assertIn('main', push['branches'])
        self.assertNotIn('paths', push, 'A later excluded main commit could strand the alias freshness guard')
        self.assertNotIn('paths-ignore', push)

    def publish(self, *, event_name='push', ref_type='branch', ref_name='main', failure=''):
        root = Path(__file__).resolve().parents[2]
        workflow = yaml.safe_load((root / '.github/workflows/publish_chart.yaml').read_text())
        step = next(step for step in workflow['jobs']['publish']['steps']
                    if 'COSIGN_PRIVATE_KEY' in step.get('env', {}))
        with tempfile.TemporaryDirectory(prefix='ilm-mcp-publish-workflow-') as temp:
            temp = Path(temp)
            stubs = temp / 'bin'
            stubs.mkdir()
            for name, body in {
                'helm': """
if sys.argv[1] == 'push':
    record('push')
    if os.environ['TEST_FAILURE_STAGE'] == 'push':
        sys.exit(9)
    print('Digest: sha256:' + 'a' * 64)
elif sys.argv[1:3] == ['registry', 'login']:
    sys.stdin.read()
""",
                'cosign': """
record('sign')
if os.environ['TEST_FAILURE_STAGE'] == 'sign':
    sys.exit(9)
""",
                'python': """
if sys.argv[1] == 'deploy/scripts/development_alias.py':
    record('alias', sys.argv[2:])
else:
    sys.exit(subprocess.run([sys.executable, *sys.argv[1:]]).returncode)
""",
            }.items():
                stub = stubs / name
                stub.write_text(f'#!{sys.executable}\n' + """
import json, os, subprocess, sys
def record(action, arguments=None):
    with open(os.environ['TEST_PUBLICATION_LOG'], 'a') as log:
        log.write(json.dumps({'action': action, 'arguments': arguments}) + '\\n')
""" + body)
                stub.chmod(0o700)
            log = temp / 'publication.jsonl'
            result = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', step['run']], cwd=root,
                env={**os.environ, 'PATH': str(stubs) + os.pathsep + os.environ['PATH'],
                     'RUNNER_TEMP': str(temp), 'GITHUB_STEP_SUMMARY': str(temp / 'summary'),
                     'TEST_PUBLICATION_LOG': str(log), 'TEST_FAILURE_STAGE': failure,
                     'EVENT_NAME': event_name, 'REF_TYPE': ref_type, 'REF_NAME': ref_name,
                     'GITHUB_SHA': 'c' * 40, 'VERSION': DEVELOP_VERSION, 'ARCHIVE': 'synthetic.tgz',
                     'HELM_USERNAME': 'synthetic-user', 'HELM_PASSWORD': 'synthetic-password',
                     'COSIGN_PRIVATE_KEY': 'synthetic-key', 'COSIGN_PASSWORD': 'synthetic-password',
                     'HELM_REGISTRY_CONFIG': str(temp / 'registry/config.json'),
                     'DOCKER_CONFIG': str(temp / 'registry')}, capture_output=True, text=True)
            events = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        return result, events

    def test_main_push_and_manual_publish_sign_before_alias(self):
        for event_name in ['push', 'workflow_dispatch']:
            with self.subTest(event_name=event_name):
                result, events = self.publish(event_name=event_name)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual([event['action'] for event in events], ['push', 'sign', 'alias'])
                args = events[-1]['arguments']
                self.assertEqual(args[args.index('--digest') + 1], SIGNED_DIGEST)
                self.assertEqual(args[args.index('--version') + 1], DEVELOP_VERSION)
                self.assertEqual(args[args.index('--sha') + 1], 'c' * 40)

    def test_release_publication_does_not_move_the_development_alias(self):
        result, events = self.publish(ref_type='tag', ref_name='v0.1.0')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([event['action'] for event in events], ['push', 'sign'])

    def test_push_or_signature_failure_never_reaches_the_alias(self):
        for failure, expected in [('push', ['push']), ('sign', ['push', 'sign'])]:
            with self.subTest(failure=failure):
                result, events = self.publish(failure=failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual([event['action'] for event in events], expected)
