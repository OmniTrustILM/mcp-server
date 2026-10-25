#!/usr/bin/env bash
# No token/secret arguments. Uses private files, keeps responses private, prints only pass/fail.
set -euo pipefail
set +x
umask 077
python3 - "$@" <<'PY'
import base64, http.client, json, os, pathlib, stat, sys, time, urllib.error, urllib.parse, urllib.request

def fail(s): raise SystemExit(s)
if len(sys.argv)!=2: fail('Usage: MCP_SECRET_FILE=... SOURCE_TOKEN_FILE=... PROBE_OUTPUT_DIR=... ./scripts/keycloak/exchange-token-probe.sh config.json')
c=json.loads(pathlib.Path(sys.argv[1]).read_text())
mode=c['mode']
if mode not in ('standard','delegation'): fail('Select mode explicitly.')
def private_file(var):
    p=pathlib.Path(os.environ[var])
    if not p.is_file() or p.stat().st_mode & 0o077: fail(var+' must refer to a private 0600 file.')
    return p.read_text().strip()
secret=private_file('MCP_SECRET_FILE')
source_input=private_file('SOURCE_TOKEN_FILE')
# The source file may be the full authorization-code token response or just access_token.
try: subject=json.loads(source_input)['access_token']
except (json.JSONDecodeError,TypeError): subject=source_input
out=pathlib.Path(os.environ['PROBE_OUTPUT_DIR'])
if not out.is_dir() or out.stat().st_mode & 0o077: fail('PROBE_OUTPUT_DIR must be an existing private directory (0700).')
def claims(token):
    try:
        raw=token.split('.')[1]; return json.loads(base64.urlsafe_b64decode(raw+'='*(-len(raw)%4)))
    except Exception: fail('Malformed JWT; token content suppressed.')
def audience(jwt):
    a=jwt.get('aud',[]); return {a} if isinstance(a,str) else set(a)
a=claims(subject)
source_subject=a.get('sub')
if not isinstance(source_subject,str) or not source_subject.strip(): fail('A subject check failed.')
if a.get('iss')!=c['issuer'] or a.get('exp',0)<=time.time(): fail('A issuer/expiry check failed.')
if not {c['resource'],'ilm-mcp'}.issubset(audience(a)) or 'ilm' in audience(a): fail('A audience isolation check failed.')
if 'mcp:access' not in a.get('scope','').split(): fail('A lacks mcp:access.')
if mode=='delegation':
    may_act=a.get('may_act')
    if (not isinstance(may_act,dict) or may_act.get('client_id')!='ilm-mcp' or
        not isinstance(may_act.get('sub'),str) or not may_act['sub'].strip()):
        fail('A lacks permitted may_act actor. Check group membership, FGAP and consent; do not fall back to standard.')
elif 'may_act' in a or 'act' in a:
    fail('Standard mode requires a fresh source token without delegation claims.')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**k): return None
opener=urllib.request.build_opener(NoRedirect)
endpoint=c['issuer'].rstrip('/')+'/protocol/openid-connect/token'
if urllib.parse.urlsplit(endpoint).scheme!='https': fail('HTTPS issuer required.')
def token_request(fields,filename):
    # Form body is constructed only in memory; no shell history, argv, verbose HTTP or response logs.
    fields={**fields,'client_id':'ilm-mcp','client_secret':secret}
    try:
        with opener.open(urllib.request.Request(endpoint,data=urllib.parse.urlencode(fields).encode(),
            headers={'Content-Type':'application/x-www-form-urlencoded','Accept':'application/json'}),timeout=30) as r:
            raw=r.read(); response=json.loads(raw)
    except urllib.error.HTTPError as e: fail('Token request failed with HTTP '+str(e.code)+'; body suppressed.')
    except (urllib.error.URLError,http.client.HTTPException,ValueError): fail('Token request failed; details suppressed.')
    if 'access_token' not in response: fail('No access token in response; body suppressed.')
    path=out/filename
    # Refuse existing/symlink targets; rerun in a new private directory.
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as f: f.write(raw)
    return response['access_token']
fields={'grant_type':'urn:ietf:params:oauth:grant-type:token-exchange','subject_token':subject,
    'subject_token_type':'urn:ietf:params:oauth:token-type:access_token',
    'requested_token_type':'urn:ietf:params:oauth:token-type:access_token',
    'audience':'ilm','scope':'openid ilm-mcp-upstream'}
if mode=='delegation':
    actor=token_request({'grant_type':'client_credentials'},'actor.json')
    fields['actor_token']=actor; fields['actor_token_type']='urn:ietf:params:oauth:token-type:access_token'
b=claims(token_request(fields,'exchanged.json'))
if b.get('iss')!=c['issuer'] or b.get('exp',0)<=time.time(): fail('B issuer/expiry check failed.')
if audience(b)!={'ilm'}: fail('B must have only the ILM API audience.')
exchanged_subject=b.get('sub')
if (not isinstance(exchanged_subject,str) or not exchanged_subject.strip() or
    exchanged_subject!=source_subject or b.get('azp')!='ilm-mcp'): fail('B subject/requester check failed.')
username=c['usernameClaim']
if not isinstance(b.get(username),str) or not b[username].strip(): fail('B existing ILM username claim missing.')
if mode=='delegation':
    act=b.get('act')
    if (not isinstance(act,dict) or act.get('client_id')!='ilm-mcp' or
        not isinstance(act.get('sub'),str) or not act['sub'].strip() or act['sub']!=a['may_act']['sub']):
        fail('B act attribution check failed.')
    if 'may_act' in b: fail('B unexpectedly carries may_act.')
elif 'act' in b or 'may_act' in b: fail('Standard mode unexpectedly returned delegation claims.')
print('PASS: token shape, audience isolation, subject continuity, username and selected-mode actor checks.')
print('Responses stored privately. JWT decoding here is a shape check; exercise signature validation and ILM authorization separately.')
PY
