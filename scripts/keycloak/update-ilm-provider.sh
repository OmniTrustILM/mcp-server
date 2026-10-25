#!/usr/bin/env bash
# Preserve full existing provider settings and its stored secret. No new issuer/provider.
set -euo pipefail
set +x
umask 077
python3 - "$@" <<'PY'
import json, os, pathlib, sys, urllib.error, urllib.parse, urllib.request
if len(sys.argv)!=2: raise SystemExit('Usage: ILM_ADMIN_TOKEN_FILE=... ./scripts/keycloak/update-ilm-provider.sh config.json')
c=json.loads(pathlib.Path(sys.argv[1]).read_text())
if c['mode']!='delegation': raise SystemExit('Standard mode needs no allowedDelegates change. Do not run this script.')
p=pathlib.Path(os.environ['ILM_ADMIN_TOKEN_FILE'])
if p.stat().st_mode & 0o077: raise SystemExit('ILM_ADMIN_TOKEN_FILE must be 0600.')
token=p.read_text().strip()
if not token or '\n' in token or '\r' in token: raise SystemExit('Use a raw bearer token in the private token file.')
base=c['ilmApiBaseUrl'].rstrip('/')
if urllib.parse.urlsplit(base).scheme!='https': raise SystemExit('HTTPS ILM base URL required.')
name=c['ilmProviderName']
if not name or name.startswith('REQUIRED_'): raise SystemExit('Supply the existing provider name.')
url=base+'/v1/settings/authentication/oauth2Providers/'+urllib.parse.quote(name,safe='')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**k): return None
opener=urllib.request.build_opener(NoRedirect)
def req(method,body=None):
    try:
        with opener.open(urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),
            headers={'Authorization':'Bearer '+token,'Accept':'application/json','Content-Type':'application/json'},method=method),timeout=30) as r:
            raw=r.read()
            value=json.loads(raw) if raw else None
            if isinstance(value,dict): value.pop('clientSecret',None)
            return value
    except urllib.error.HTTPError as e: raise SystemExit('ILM provider '+method+' HTTP '+str(e.code)+'; response suppressed.')
    except urllib.error.URLError: raise SystemExit('ILM connection/TLS failure; details suppressed.')
p=req('GET')
if p.get('issuerUrl')!=c['issuer'] or p.get('clientId')!='ilm': raise SystemExit('Existing issuer/provider client ID differs; stop and review.')
if 'allowedDelegates' not in p or 'delegateClaim' not in p:
    raise SystemExit('Live ILM does not expose delegation provider fields; deploy the matching ILM change first.')
if p.get('delegateClaim')!='client_id': raise SystemExit('Existing delegateClaim differs; review before changing it.')
# The source-confirmed provider PUT persists the full DTO. Delete response-only fields and
# OMIT clientSecret: the matched ILM implementation preserves its stored secret on omission.
for key in ('name','jwkSetKeys','jwkSetLoadFailure','clientSecret'): p.pop(key,None)
p['allowedDelegates']=list(dict.fromkeys([*(p.get('allowedDelegates') or []),'ilm-mcp']))
req('PUT',p)
after=req('GET')
if after.get('issuerUrl')!=c['issuer'] or after.get('clientId')!='ilm' or 'ilm-mcp' not in after.get('allowedDelegates',[]):
    raise SystemExit('Provider read-back failed; review configuration without logging secrets.')
print('Updated existing provider allowedDelegates additively; existing secret omitted and preserved by server semantics.')
PY
