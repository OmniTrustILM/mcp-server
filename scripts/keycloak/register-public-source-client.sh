#!/usr/bin/env bash
# Creates a new dedicated public native OAuth client, or validates an exact existing registration.
# Never PUTs existing clients or changes their credentials/redirects.
set -euo pipefail
set +x
umask 077
python3 - "$@" <<'PY'
import json,os,pathlib,sys,urllib.error,urllib.parse,urllib.request
if len(sys.argv)!=3:raise SystemExit('Usage: KC_ADMIN_TOKEN_FILE=... ./scripts/keycloak/register-public-source-client.sh config.json public-client.json')
c=json.loads(pathlib.Path(sys.argv[1]).read_text());wanted=json.loads(pathlib.Path(sys.argv[2]).read_text())
base=c['keycloakBaseUrl'].rstrip('/');realm=c['realm']
if urllib.parse.urlsplit(base).scheme!='https' or realm.startswith('REQUIRED_'):raise SystemExit('Set exact HTTPS Keycloak base and existing realm.')
fixed={'protocol':'openid-connect','enabled':True,'publicClient':True,'bearerOnly':False,
 'standardFlowEnabled':True,'implicitFlowEnabled':False,'directAccessGrantsEnabled':False,
 'serviceAccountsEnabled':False,'fullScopeAllowed':False,'webOrigins':[]}
allowed=set(fixed)|{'clientId','redirectUris','consentRequired','attributes'}
if set(wanted)-allowed or any(wanted.get(k)!=v for k,v in fixed.items()):raise SystemExit('Use the dedicated public authorization-code-only template; extra fields prohibited.')
cid=wanted.get('clientId','')
if not cid.startswith('ilm-mcp-') or cid=='ilm-mcp':raise SystemExit('Use a distinct dedicated frontend client ID, such as ilm-mcp-codex.')
if wanted.get('attributes')!={'pkce.code.challenge.method':'S256'}:raise SystemExit('PKCE S256 is mandatory.')
if wanted.get('consentRequired') is not True:raise SystemExit('These templates require consent for delegation; leave it enabled.')
redirects=wanted.get('redirectUris')
if not isinstance(redirects,list) or not redirects:raise SystemExit('Supply exact callback URI(s) displayed/documented by the chosen client.')
for uri in redirects:
 if not isinstance(uri,str) or 'REQUIRED_' in uri or '*' in uri:raise SystemExit('Replace callback placeholders with exact URI(s); wildcards prohibited.')
 u=urllib.parse.urlsplit(uri)
 if not u.hostname or u.fragment or (u.scheme!='https' and not (u.scheme=='http' and u.hostname in ('localhost','127.0.0.1','::1'))):
  raise SystemExit('Callback must be the exact client-provided HTTPS or loopback HTTP URI.')
p=pathlib.Path(os.environ['KC_ADMIN_TOKEN_FILE'])
if not p.is_file() or p.stat().st_mode & 0o077:raise SystemExit('Private 0600 admin token file required.')
t=p.read_text().strip()
if not t or '\n' in t or '\r' in t:raise SystemExit('Invalid bearer token file.')
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*a,**k):return None
opener=urllib.request.build_opener(NoRedirect)
prefix=base+'/admin/realms/'+urllib.parse.quote(realm,safe='')+'/clients'
def request(method,url,body=None):
 try:
  with opener.open(urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),
    headers={'Authorization':'Bearer '+t,'Accept':'application/json','Content-Type':'application/json'},method=method),timeout=30) as r:
   raw=r.read();value=json.loads(raw) if raw else None
   for row in value if isinstance(value,list) else [value]:
    if isinstance(row,dict):
     row.pop('secret',None);row.pop('registrationAccessToken',None)
     (row.get('attributes') or {}).pop('client.secret.rotated',None)
   return value
 except urllib.error.HTTPError as e:raise SystemExit('Public client '+method+' HTTP '+str(e.code)+'; response suppressed.')
 except urllib.error.URLError:raise SystemExit('Public client connection/TLS failure; details suppressed.')
rows=[r for r in request('GET',prefix+'?'+urllib.parse.urlencode({'clientId':cid})) if r.get('clientId')==cid]
if len(rows)>1:raise SystemExit('Ambiguous existing client ID.')
if rows:
 existing=request('GET',prefix+'/'+urllib.parse.quote(rows[0]['id'],safe=''))
 for key,value in wanted.items():
  if key=='attributes':
   same=all((existing.get('attributes') or {}).get(k)==v for k,v in value.items())
  elif key=='redirectUris':same=set(existing.get(key,[]))==set(value)
  else:same=existing.get(key)==value
  if not same:raise SystemExit('Existing source client differs at '+key+'; no mutation performed. Review its existing registration separately.')
 print('Existing public source registration matches; no mutation performed.')
else:
 request('POST',prefix,wanted)
 print('Dedicated public source client created with exact supplied callbacks. Add its clientId to sourceClientIds, then run configure-keycloak.sh.')
PY
