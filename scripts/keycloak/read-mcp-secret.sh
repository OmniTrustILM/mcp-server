#!/usr/bin/env bash
# Operator-only GET of the current secret. Never rotates credentials or prints response bodies.
set -euo pipefail
set +x
umask 077
python3 - "$@" <<'PY'
import json,os,pathlib,sys,urllib.error,urllib.parse,urllib.request
if len(sys.argv)!=3:raise SystemExit('Usage: KC_ADMIN_TOKEN_FILE=... ./scripts/keycloak/read-mcp-secret.sh config.json /private/path/mcp.secret')
c=json.loads(pathlib.Path(sys.argv[1]).read_text());p=pathlib.Path(os.environ['KC_ADMIN_TOKEN_FILE'])
if p.stat().st_mode & 0o077:raise SystemExit('Admin token file must be private (0600).')
t=p.read_text().strip()
if not t or '\n' in t or '\r' in t:raise SystemExit('Invalid private bearer token file.')
base=c['keycloakBaseUrl'].rstrip('/')
if urllib.parse.urlsplit(base).scheme!='https':raise SystemExit('HTTPS required.')
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*a,**k):return None
opener=urllib.request.build_opener(NoRedirect)
prefix=base+'/admin/realms/'+urllib.parse.quote(c['realm'],safe='')+'/clients'
def get(url):
 try:
  with opener.open(urllib.request.Request(url,headers={'Authorization':'Bearer '+t,'Accept':'application/json'}),timeout=30) as r:
   value=json.loads(r.read())
   if isinstance(value,list):
    for row in value:
     row.pop('secret',None);row.pop('registrationAccessToken',None)
     (row.get('attributes') or {}).pop('client.secret.rotated',None)
   return value
 except urllib.error.HTTPError as e:raise SystemExit('Admin GET failed: HTTP '+str(e.code)+'; response suppressed.')
 except urllib.error.URLError:raise SystemExit('Admin connection/TLS failure; details suppressed.')
rows=[x for x in get(prefix+'?clientId=ilm-mcp') if x.get('clientId')=='ilm-mcp']
if len(rows)!=1:raise SystemExit('Expected one existing ilm-mcp client.')
out=pathlib.Path(sys.argv[2]);parent=out.parent
if not parent.is_dir() or parent.stat().st_mode & 0o077:raise SystemExit('Output parent must be a private directory (0700).')
v=get(prefix+'/'+urllib.parse.quote(rows[0]['id'],safe='')+'/client-secret').get('value')
if not isinstance(v,str) or not v:raise SystemExit('No existing secret returned; no credential rotation attempted.')
fd=os.open(out,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write(v)
print('Current client secret saved privately; no response body or secret printed. No rotation performed.')
PY
