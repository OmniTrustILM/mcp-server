"""Point Harbor's development chart tag at an already published, signed digest."""
import argparse
import base64
from http.client import HTTPException
import json
import os
import re
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener


ARTIFACTS_URL = ('https://hub.omnitrustregistry.com/api/v2.0/projects/ilm-helm/'
                 'repositories/mcp-server/artifacts/')
ALIAS = '0.0.0'
DIGEST = re.compile(r'sha256:[a-f0-9]{64}')


class PublicationError(RuntimeError):
    """A sanitized publication failure, safe for the workflow log."""

    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class HarborClient:
    def __init__(self, username, password, *, opener=None):
        if not username or not password:
            raise PublicationError('Harbor credentials are required')
        credentials = base64.b64encode(f'{username}:{password}'.encode()).decode()
        self.authorization = f'Basic {credentials}'
        self.opener = opener if opener is not None else build_opener(NoRedirect())

    def _request(self, method, reference, suffix='', payload=None):
        if reference != ALIAS and not DIGEST.fullmatch(reference):
            raise PublicationError('Invalid Harbor artifact reference')
        request = Request(ARTIFACTS_URL + quote(reference, safe='') + suffix,
                          data=json.dumps(payload).encode() if payload is not None else None,
                          headers={'Authorization': self.authorization,
                                   'Content-Type': 'application/json', 'Accept': 'application/json'},
                          method=method)
        expected_status = 201 if method == 'POST' else 200
        try:
            with self.opener.open(request, timeout=30) as response:
                if response.status != expected_status:
                    raise PublicationError(f'Harbor {method} returned HTTP {response.status}', response.status)
                if method != 'GET':
                    return None
                # Artifact metadata is small; reject unbounded or malformed responses.
                body = response.read(65537)
                if len(body) > 65536:
                    raise PublicationError('Harbor artifact metadata is too large')
        except HTTPError as error:
            error.close()
            raise PublicationError(f'Harbor {method} failed with HTTP {error.code}', error.code) from None
        except (OSError, HTTPException):
            raise PublicationError(f'Harbor {method} request failed') from None
        try:
            artifact = json.loads(body)
        except ValueError:
            raise PublicationError('Harbor returned invalid artifact metadata') from None
        digest = artifact.get('digest') if isinstance(artifact, dict) else None
        if not isinstance(digest, str) or not DIGEST.fullmatch(digest):
            raise PublicationError('Harbor returned an invalid artifact digest')
        return digest

    def artifact_digest(self, reference):
        return self._request('GET', reference)

    def delete_alias(self, digest):
        self._request('DELETE', digest, f'/tags/{ALIAS}')

    def create_alias(self, digest):
        self._request('POST', digest, '/tags', {'name': ALIAS})


def _publish_alias(client, previous, digest):
    try:
        client.create_alias(digest)
    except PublicationError:
        if previous is None:
            raise
    else:
        if client.artifact_digest(ALIAS) != digest:
            raise PublicationError('Harbor development alias does not match the signed chart digest')
        return
    try:
        current = client.artifact_digest(ALIAS)
    except PublicationError as error:
        if error.status != 404:
            raise PublicationError('Harbor development alias recovery lookup failed') from None
    else:
        if current == digest:
            return
        raise PublicationError('Harbor development alias changed during publication') from None
    try:
        client.create_alias(previous)
        restored = client.artifact_digest(ALIAS)
    except PublicationError:
        raise PublicationError('Harbor development alias recovery failed') from None
    if restored != previous:
        raise PublicationError('Harbor previous development alias could not be verified') from None
    raise PublicationError('Harbor development alias update failed; previous alias restored') from None


def update_alias(*, event_name, ref_type, ref_name, version, digest, client):
    if event_name not in ('push', 'workflow_dispatch') or (ref_type, ref_name) != ('branch', 'main'):
        raise PublicationError('The development alias is restricted to main branch publication')
    if not re.fullmatch(r'\d+\.\d+\.\d+-develop', version):
        raise PublicationError('The development alias requires a development chart version')
    if not DIGEST.fullmatch(digest):
        raise PublicationError('The development alias requires a sha256 artifact digest')
    if client.artifact_digest(digest) != digest:
        raise PublicationError('Harbor target does not match the signed chart digest')
    try:
        previous = client.artifact_digest(ALIAS)
    except PublicationError as error:
        if error.status != 404:
            raise
        previous = None
    if previous == digest:
        return
    if previous is not None:
        client.delete_alias(previous)
    _publish_alias(client, previous, digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--event-name', required=True)
    parser.add_argument('--ref-type', required=True)
    parser.add_argument('--ref-name', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--digest', required=True)
    args = parser.parse_args()
    try:
        client = HarborClient(os.environ.get('HELM_USERNAME'), os.environ.get('HELM_PASSWORD'))
        update_alias(**vars(args), client=client)
    except PublicationError as error:
        print(f'Development alias publication failed: {error}', file=sys.stderr)
        return 1
    print('Verified Harbor development alias 0.0.0 at the signed chart digest')
    return 0


if __name__ == '__main__':
    sys.exit(main())
