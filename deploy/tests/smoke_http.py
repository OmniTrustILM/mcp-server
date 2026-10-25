"""Probe the actual shared HTTP server through kubectl port-forward."""
import json
import sys
import urllib.error
import urllib.request


expected_additional_scopes = sys.argv[1:] or ['openid']
base = 'http://127.0.0.1:18080'
for path in ['/actuator/health/liveness', '/actuator/health/readiness']:
    with urllib.request.urlopen(base + path, timeout=10) as response:
        assert response.status == 200
        assert json.load(response)['status'] == 'UP'

with urllib.request.urlopen(base + '/.well-known/oauth-protected-resource/mcp', timeout=10) as response:
    metadata = json.load(response)
    assert metadata['resource'] == 'https://ilm.example.invalid/mcp'
    assert metadata['authorization_servers'] == ['https://identity.example.invalid/tenant']
    assert set(metadata['scopes_supported']) == {'mcp:access', *expected_additional_scopes}

for headers in [{}, {'Authorization': 'Bearer synthetic-invalid-token'}]:
    request = urllib.request.Request(base + '/mcp', headers=headers, data=b'{}')
    try:
        urllib.request.urlopen(request, timeout=10)
        raise AssertionError('unauthenticated MCP request unexpectedly succeeded')
    except urllib.error.HTTPError as response:
        assert response.code == 401, response.code
        assert 'resource_metadata=' in response.headers['WWW-Authenticate']
print('Installed chart: health, OAuth discovery and authentication rejection passed')
