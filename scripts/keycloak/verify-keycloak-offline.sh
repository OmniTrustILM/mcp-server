#!/usr/bin/env bash
# Offline fixture only. All HTTP is intercepted; no live network/credentials are used.
set -euo pipefail
set +x
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
python3 - "$SCRIPT_DIR" <<'PY'
import ast,base64,contextlib,copy,http.client,io,json,os,pathlib,sys,tempfile,time,traceback,urllib.parse,urllib.request
root=pathlib.Path(sys.argv[1])
examples=root.parent.parent/'docs'/'keycloak'
code=(root/'configure-keycloak.sh').read_text().split("<<'PY'\n",1)[1].rsplit('\nPY',1)[0]
ast.parse(code)
ROTATION_KEYS=('client.secret.rotation.enabled','client.secret.expiration.time',
 'client.secret.remaining.expiration.time','client.secret.rotated',
 'client.secret.rotated.creation.time','client.secret.rotated.expiration.time')
SECRET='OFFLINE_PRIMARY_SECRET_SENTINEL';ROTATED='OFFLINE_ROTATED_SECRET_SENTINEL';REG='OFFLINE_REGISTRATION_SENTINEL'
class Response:
 def __init__(self,value):self.value=copy.deepcopy(value)
 def __enter__(self):return self
 def __exit__(self,*args):pass
 def read(self):return b'' if self.value is None else json.dumps(self.value).encode()
class Fixture:
 def __init__(self,actor=True):
  self.clients={'ilm':dict(id='ilm-id',clientId='ilm',secret=SECRET,registrationAccessToken=REG),
   'source':dict(id='source-id',clientId='source',protocol='openid-connect',enabled=True,bearerOnly=False,
    publicClient=True,standardFlowEnabled=True,implicitFlowEnabled=False,directAccessGrantsEnabled=False,serviceAccountsEnabled=False,
    fullScopeAllowed=False,consentRequired=True,redirectUris=['https://source.invalid/callback'],
    attributes={'keep':'yes','pkce.code.challenge.method':'S256',
     'oauth2.device.authorization.grant.enabled':'false','oidc.ciba.grant.enabled':'false','oauth2.jwt.authorization.grant.enabled':'false'},secret=SECRET,registrationAccessToken=REG)}
  self.scopes={n:dict(id=n+'-id',name=n,protocol='openid-connect') for n in ('delegation:client','profile')}
  self.assigned={'ilm-id':{'default':[self.scopes['profile']],'optional':[]}}
  self.mappers={};self.groups=[];self.policies={};self.members=set();self.writes=[];self.reads=[]
  self.realm={'adminPermissionsEnabled':False,'untouched':42};self.sa={};self.sa_generation=0
  if actor:
   self.clients['ilm-mcp']=dict(id='actor-id',clientId='ilm-mcp',protocol='openid-connect',enabled=True,
    publicClient=False,bearerOnly=False,clientAuthenticatorType='client-secret',standardFlowEnabled=False,
    implicitFlowEnabled=False,directAccessGrantsEnabled=False,serviceAccountsEnabled=True,
    fullScopeAllowed=False,consentRequired=False,secret=SECRET,registrationAccessToken=REG,
    attributes={'standard.token.exchange.enabled':'true','standard.token.exchange.enableRefreshRequestedTokenType':'NO'})
   self.sa['actor-id']='service-account-original'
 def open(self,r,timeout):
  u=urllib.parse.urlsplit(r.full_url)
  assert u.hostname=='offline.invalid','Unexpected URL, no network permitted'
  path=urllib.parse.unquote(u.path).removeprefix('/admin/realms/fixture');query=urllib.parse.parse_qs(u.query)
  method=r.method;parts=path.split('/');body=json.loads(r.data) if r.data is not None else None;result=None
  if method=='GET':self.reads.append(path)
  else:self.writes.append((method,path,copy.deepcopy(body)))
  if path=='':
   if method=='GET':result=self.realm
   else:
    self.realm.update(body);self.clients['admin-permissions']=dict(id='admin-id',clientId='admin-permissions')
  elif path=='/clients':
   if method=='GET':result=[c for c in self.clients.values() if c['clientId']==query['clientId'][0]]
   else:
    body.update(id='actor-id' if body['clientId']=='ilm-mcp' else body['clientId']+'-id',secret=SECRET,registrationAccessToken=REG);self.clients[body['clientId']]=body
    if body.get('serviceAccountsEnabled'):self.enable_sa(body['id'])
  elif path=='/client-scopes':
   if method=='GET':result=list(self.scopes.values())
   else:body['id']=body['name']+'-id';self.scopes[body['name']]=body
  elif path=='/groups':
   if method=='GET':result=self.groups
   else:self.groups.append(dict(id='group-id',name=body['name'],path='/'+body['name']))
  elif path.startswith('/users/'):
   if method=='GET':result={'id':parts[2],'username':'selected-human'}
   else:self.members.add(path)
  elif '/authz/' in path:
   if method=='GET':result=self.policies.get(query['name'][0])
   else:
    if method=='POST':body['id']=body['name']+'-id'
    self.policies[body['name']]=body;result=body if method=='POST' else None
  elif path.startswith('/clients/'):
   c=next(c for c in self.clients.values() if c['id']==parts[2])
   if len(parts)==3:
    if method=='GET':result=c
    else:
     # Model the version-pinned 26.8.1 hazards rather than plain JSON preservation.
     previous_sa=c.get('serviceAccountsEnabled',False);c.update(body)
     for key in ROTATION_KEYS[1:]:c.setdefault('attributes',{}).pop(key,None)
     if body.get('serviceAccountsEnabled') is False:self.sa.pop(c['id'],None)
     elif body.get('serviceAccountsEnabled') and not previous_sa:self.enable_sa(c['id'])
   elif parts[3]=='service-account-user':result={'id':self.sa[c['id']]}
   elif parts[3]=='protocol-mappers':result=[]
   elif parts[3].endswith('-client-scopes'):
    kind=parts[3].split('-')[0];a=self.assigned.setdefault(parts[2],{'default':[],'optional':[]})
    if method=='GET':result=a[kind]
    else:
     s=next(s for s in self.scopes.values() if s['id']==parts[4])
     if not any(x['id']==s['id'] for x in a[kind]):a[kind].append(s)
   else:raise AssertionError(path)
  elif path.startswith('/client-scopes/'):
   if parts[3]=='protocol-mappers':
    if method=='GET':result=self.mappers.get(parts[2],[])
    else:body['id']=body['name']+'-id';self.mappers.setdefault(parts[2],[]).append(body)
   elif parts[3]=='scope-mappings':result={}
   else:raise AssertionError(path)
  else:raise AssertionError(path)
  return Response(result)
 def enable_sa(self,cid):
  self.sa_generation+=1;self.sa[cid]='service-account-created-'+str(self.sa_generation)
 def client_puts(self):return [w for w in self.writes if w[0]=='PUT' and w[1].startswith('/clients/') and len(w[1].split('/'))==3]

def config(mode='delegation'):
 c=json.loads((examples/'keycloak-input.example.json').read_text())
 c.update(keycloakBaseUrl='https://offline.invalid',realm='fixture',issuer='https://offline.invalid/realms/fixture',
  resource='https://ilm.example.invalid/mcp',mode=mode,sourceClientIds=['source'],targetScopeNames=['profile'],usernameClaim='preferred_username',
  mappingSelectionReviewed=True,sourceAudienceConfigurationReviewed=True,selectedGroupMemberUserIds=['human-id'])
 return c

def run(fixture,c,action='apply',expect_failure=False):
 with tempfile.TemporaryDirectory() as d:
  d=pathlib.Path(d);t=d/'token';t.write_text('offline-invalid-not-a-credential');t.chmod(0o600)
  os.environ['KC_ADMIN_TOKEN_FILE']=str(t);cf=d/'config.json';cf.write_text(json.dumps(c))
  output=io.StringIO();namespace={};old=urllib.request.build_opener;oldargv=sys.argv
  urllib.request.build_opener=lambda *args:fixture;sys.argv=['-',action,str(cf)];failure=None
  try:
   with contextlib.redirect_stdout(output):
    try:exec(compile(code,'configure-keycloak.sh','exec'),namespace)
    except SystemExit as e:
     if e.code not in (None,0):failure=str(e)
  finally:urllib.request.build_opener=old;sys.argv=oldargv
  fixture.last_output=output.getvalue()
  assert bool(failure)==expect_failure,(failure,output.getvalue())
  text=output.getvalue()+(failure or '')
  assert not any(s in text for s in (SECRET,ROTATED,REG)),'Credential leaked to output'
  assert not any(s in json.dumps(fixture.writes) for s in (SECRET,ROTATED,REG)),'Credential leaked to request body'
  # Returned configuration objects must be scrubbed even when GETs contain sentinels.
  for name in ('actor','ilm','sources','actor_patch','source_updates','found','c'):
   value=namespace.get(name)
   if isinstance(value,(dict,list)):
    assert not any(s in json.dumps(value) for s in (SECRET,ROTATED,REG)),name+' retained secret'
  return failure

# A resource must be deployment-configurable and reach the audience mapper
# verbatim, without normalization or substitution with a client ID.
for selected_mode in ('standard','delegation'):
 for resource in ('https://ilm.example.invalid/mcp','https://mcp.example.invalid:9443/custom/mcp%2Dresource',
                  'https://[2001:db8::1]:9443/mcp','https://192.0.2.1/mcp'):
  f=Fixture();c=config(selected_mode);c['resource']=resource
  run(f,c);run(f,c)
  mappers=f.mappers['mcp:access-id']
  resource_mappers=[m for m in mappers if m['name']=='mcp-public-resource']
  assert len(resource_mappers)==1,(selected_mode,resource,mappers)
  assert resource_mappers[0]['config']['included.custom.audience']==resource
  assert next(m for m in mappers if m['name']=='mcp-exchange-requester')['config']['included.client.audience']=='ilm-mcp'
  assert next(m for m in f.mappers['ilm-mcp-upstream-id'] if m['name']=='ilm-api-audience')['config']['included.client.audience']=='ilm'
print('PASS: configured HTTPS resources on distinct reserved origins reach dedicated audience mappers exactly and remain idempotent in both modes.')

# URI parser tolerance must not permit insecure, ambiguous or placeholder
# resources to cause a Keycloak read or mutation, including inspection.
unsafe_resources=(None,'',42,[],{},'ilm','ilm-mcp','/mcp','http://ilm.example.invalid/mcp',
 'https://','https:///mcp','https://OFFLINE_RESOURCE_USER:OFFLINE_RESOURCE_PASSWORD@ilm.example.invalid/mcp',
 'https://ilm.example.invalid/mcp?query=1','https://ilm.example.invalid/mcp?',
 'https://ilm.example.invalid/mcp#fragment','https://ilm.example.invalid/mcp#',
 'https://ilm.example.invalid\\evil/mcp','https://ilm.example.invalid/mcp%5Cevil',
 ' https://ilm.example.invalid/mcp','https://ilm.example.invalid/mcp ','https://ilm.example.invalid/mcp%20',
 '\thttps://ilm.example.invalid/mcp','https://ilm.example.invalid/mcp\n','https://ilm.example.invalid/mcp%0A',
 'https://ilm.example.invalid/mcp%7F','https://ilm.example.invalid/mcp%C2%80',
 'https://ilm.example.invalid/mcp%','https://ilm.example.invalid/mcp%ZZ','https://ilm.example.invalid/mcpé',
 'https://*.example.invalid/mcp','https://ilm.example.invalid/*',
 'https://ilm.example.invalid/%2a','https://ilm.example.invalid/<resource>',
 'https://ilm.example.invalid/%7Bresource%7D','https://REQUIRED_HOST/mcp',
 'https://bad_host.example.invalid/mcp','https://.example.invalid/mcp','https://ilm..example.invalid/mcp',
 'https://ilm.example.invalid../mcp','https://ilm.example.invalid/mcp|','https://ilm.example.invalid/mcp"',
 'https://ilm.example.invalid/mcp^','https://ilm.example.invalid/mcp`','https://ilm.example.invalid/mcp[',
 'https://-ilm.example.invalid/mcp','https://ilm-.example.invalid/mcp','https://999.999.999.999/mcp',
 'https://ilm.example.invalid:0/mcp','https://ilm.example.invalid:-1/mcp','https://ilm.example.invalid:70000/mcp',
 'https://ilm.example.invalid:invalid/mcp','https://ilm.example.invalid:/mcp',
 'https://[2001:db8::1]trailing/mcp','https://ilm%2eexample.invalid/mcp','https://[2001:db8::1%25zone]/mcp')
for action in ('apply','inspect'):
 for selected_mode in ('standard','delegation'):
  for resource in unsafe_resources:
   f=Fixture(actor=False);c=config(selected_mode);c['resource']=resource
   message=run(f,c,action=action,expect_failure=True)
   assert not f.writes and not f.reads,(action,selected_mode,resource,f.reads,f.writes)
   assert 'resource' in message.lower(),(resource,message)
   assert 'OFFLINE_RESOURCE_USER' not in message and 'OFFLINE_RESOURCE_PASSWORD' not in message
  for client_key in ('apiClientId','actorClientId','sourceClientIds'):
   f=Fixture(actor=False);c=config(selected_mode)
   c[client_key]=[c['resource']] if client_key=='sourceClientIds' else c['resource']
   run(f,c,action=action,expect_failure=True);assert not f.writes and not f.reads
print('PASS: malformed/insecure/placeholder HTTPS resources and audience/client-ID collisions fail before any Keycloak reads or writes in both modes and inspection.')

# Attaching mcp:access must not expose password/implicit/non-code grants or
# a loose callback, even when ordinary mode does not require browser consent.
unsafe_sources=[
 ('password',{'directAccessGrantsEnabled':True}),
 ('implicit',{'implicitFlowEnabled':True}),
 ('service-account',{'serviceAccountsEnabled':True}),
 ('device',{'attribute':('oauth2.device.authorization.grant.enabled','true')}),
 ('ciba',{'attribute':('oidc.ciba.grant.enabled','true')}),
 ('jwt-assertion',{'attribute':('oauth2.jwt.authorization.grant.enabled','true')}),
 ('wildcard',{'redirectUris':['https://source.invalid/*']}),
 ('encoded-wildcard',{'redirectUris':['https://source.invalid/%2A']}),
 ('missing-callback',{'redirectUris':[]}),
 ('nonexact-placeholder',{'redirectUris':['https://source.invalid/<callback>']}),
 ('relative-callback',{'redirectUris':['/callback']}),
 ('remote-http',{'redirectUris':['http://source.invalid/callback']}),
 ('callback-userinfo',{'redirectUris':['https://user:password@source.invalid/callback']}),
 ('callback-fragment',{'redirectUris':['https://source.invalid/callback#fragment']}),
 ('empty-callback-fragment',{'redirectUris':['https://source.invalid/callback#']}),
 ('callback-control',{'redirectUris':['https://source.invalid/callback%0A']}),
 ('callback-invalid-port',{'redirectUris':['https://source.invalid:70000/callback']}),
 ('wrong-protocol',{'protocol':'saml'}),
 ('bearer-only',{'bearerOnly':True}),
 ('disabled',{'enabled':False}),
]
for selected_mode in ('standard','delegation'):
 for name,patch in unsafe_sources:
  f=Fixture(actor=False);source=f.clients['source']
  if 'attribute' in patch:
   key,value=patch['attribute'];source['attributes'][key]=value
  else:source.update(patch)
  before=copy.deepcopy(source)
  run(f,config(selected_mode),expect_failure=True)
  assert not f.writes,(selected_mode,name,f.writes)
  assert source==before,(selected_mode,name,'unsafe source was silently changed')
print('PASS: 40 unsafe source cases across both modes reject non-code grants, nonexact/unsafe callbacks and invalid client identity before all writes.')

# Inspection must expose the actual grant/callback/PKCE settings while keeping
# credential sentinels suppressed, even for a source that apply would reject.
f=Fixture();f.clients['source'].update(implicitFlowEnabled=True,directAccessGrantsEnabled=True,serviceAccountsEnabled=True)
f.clients['source']['attributes'].update({'oauth2.device.authorization.grant.enabled':'true','oidc.ciba.grant.enabled':'true','oauth2.jwt.authorization.grant.enabled':'true','pkce.code.challenge.method':'plain'})
run(f,config(),action='inspect');assert not f.writes
source_report=json.loads(f.last_output)['existingSourceClients'][0]
for name in ('protocol','enabled','bearerOnly','publicClient','standardFlowEnabled','implicitFlowEnabled','directAccessGrantsEnabled','serviceAccountsEnabled','redirectUris'):
 assert source_report[name]==f.clients['source'][name],name
assert source_report['grantAttributes']=={k:f.clients['source']['attributes'][k] for k in ('oauth2.device.authorization.grant.enabled','oidc.ciba.grant.enabled','oauth2.jwt.authorization.grant.enabled')}
assert source_report['pkceCodeChallengeMethod']=='plain'
print('PASS: inspection exposes exact source protocol/flow flags, device/CIBA/JWT grant attributes, callbacks and PKCE without credential values.')
f=Fixture();f.clients['source']['redirectUris']=['https://OFFLINE_URI_USER:OFFLINE_URI_PASSWORD@source.invalid/callback']
run(f,config(),action='inspect');assert not f.writes
assert 'OFFLINE_URI_USER' not in f.last_output and 'OFFLINE_URI_PASSWORD' not in f.last_output
assert json.loads(f.last_output)['existingSourceClients'][0]['redirectUris']==['<unsafe callback: embedded credentials redacted>']
for selected_mode in ('standard','delegation'):
 f=Fixture();f.clients['source']['redirectUris']=['http://127.0.0.1:19483/callback','http://localhost:19483/callback','http://[::1]:19483/callback','https://source.invalid/exact-callback']
 f.clients['source']['attributes']['pkce.code.challenge.method']='plain'
 original_flows={k:f.clients['source'][k] for k in ('implicitFlowEnabled','directAccessGrantsEnabled','serviceAccountsEnabled')}
 run(f,config(selected_mode))
 assert f.clients['source']['attributes']['pkce.code.challenge.method']=='S256'
 assert original_flows=={k:f.clients['source'][k] for k in original_flows}
 f=Fixture(actor=False);f.clients['source']['attributes'].update({'pkce.code.challenge.method':'plain','client.secret.expiration.time':'123'})
 run(f,config(selected_mode),expect_failure=True);assert not f.writes
print('PASS: unsafe callback userinfo is redacted; exact HTTPS/loopback callbacks work; explicit S256 tightening preserves flows and still respects rotation preflight.')


# Rotation in ANY existing client that needs PUT must abort before ANY write,
# including when the requester itself has not yet been created.
for key in ROTATION_KEYS:
 f=Fixture();f.clients['ilm-mcp']['attributes'][key]=ROTATED if key=='client.secret.rotated' else '123'
 f.clients['ilm-mcp']['attributes']['standard.token.exchange.enabled']='false'
 message=run(f,config(),expect_failure=True);assert not f.writes and 'before any mutation' in message
 f=Fixture(actor=False);f.clients['source']['fullScopeAllowed']=True
 f.clients['source']['attributes'][key]=ROTATED if key=='client.secret.rotated' else '123'
 run(f,config(),expect_failure=True);assert not f.writes
print('PASS: all six exact rotation-state keys block needed actor/source PUTs before any mutation.')

# A configured client with rotation state may still run without an unnecessary
# ClientResource PUT; its server-side rotation attributes remain untouched.
f=Fixture();f.clients['ilm-mcp']['attributes'].update({key:(ROTATED if key=='client.secret.rotated' else '123') for key in ROTATION_KEYS})
before=copy.deepcopy(f.clients['ilm-mcp']);run(f,config());run(f,config())
assert not f.client_puts() and f.clients['ilm-mcp']==before
assert sum(w[0]=='POST' and w[1]=='/client-scopes' for w in f.writes)==2
assert len(f.groups)==1 and len(f.members)==1
assert f.policies['ilm-mcp-selected-users-delegation']['resources']==['group-id']
assert f.policies['ilm-mcp-selected-users-delegation']['scopes']==['delegate-members']
print('PASS: unchanged client PUTs skipped, two applies retain rotation state and dedicated object/group idempotence.')

f=Fixture();sa=f.sa['actor-id']
for mode in ('delegation','standard','delegation'):
 run(f,config(mode));assert f.sa['actor-id']==sa and f.clients['ilm-mcp']['serviceAccountsEnabled'] is True
assert not f.client_puts()
print('PASS: delegation -> standard -> delegation retains the same service-account UUID without client PUTs.')

f=Fixture(actor=False);run(f,config('standard'))
assert f.clients['ilm-mcp']['serviceAccountsEnabled'] is False and 'actor-id' not in f.sa
run(f,config('delegation'));sa=f.sa['actor-id'];run(f,config('standard'));run(f,config('delegation'))
assert f.sa['actor-id']==sa and f.clients['ilm-mcp']['serviceAccountsEnabled'] is True
print('PASS: new standard requester starts without service account; later delegation creates it once and later modes retain it.')

f=Fixture();f.clients['source']['fullScopeAllowed']=True
run(f,config());assert f.clients['source']['publicClient'] is True
assert f.clients['source']['redirectUris']==['https://source.invalid/callback']
assert f.clients['source']['attributes']['keep']=='yes'
assert f.realm['untouched']==42
f=Fixture();f.clients['source']['publicClient']=False
run(f,config(),expect_failure=True);assert not f.writes
print('PASS: source remains pre-registered/public, redirects/unrelated fields retained, confidential source rejected before mutation.')

f=Fixture();f.clients['ilm']['attributes']={'client.secret.rotated':ROTATED}
run(f,config(),action='inspect');assert not f.writes and '/clients/ilm-id' not in f.reads
print('PASS: inspection avoids full unrelated ILM client GET; primary/rotated/registration sentinels absent from output/config/request bodies.')
registration_code=(root/'register-public-source-client.sh').read_text().split("<<'PY'\n",1)[1].rsplit('\nPY',1)[0]
ast.parse(registration_code)
def register(f,payload,expect_failure=False):
 with tempfile.TemporaryDirectory() as d:
  d=pathlib.Path(d);token=d/'token';token.write_text('offline-invalid');token.chmod(0o600)
  os.environ['KC_ADMIN_TOKEN_FILE']=str(token);cf=d/'config';cf.write_text(json.dumps(config()))
  pf=d/'public';pf.write_text(json.dumps(payload));old=urllib.request.build_opener;oldargv=sys.argv
  urllib.request.build_opener=lambda *a:f;sys.argv=['-',str(cf),str(pf)];output=io.StringIO();failure=None
  try:
   with contextlib.redirect_stdout(output):
    try:exec(compile(registration_code,'registration','exec'),{})
    except SystemExit as e:
     if e.code not in (None,0):failure=str(e)
  finally:urllib.request.build_opener=old;sys.argv=oldargv
  assert bool(failure)==expect_failure,(failure,output.getvalue())
  assert not any(v in output.getvalue()+(failure or '')+json.dumps(f.writes) for v in (SECRET,ROTATED,REG,'OFFLINE_URI_USER','OFFLINE_URI_PASSWORD'))
for frontend in ('codex','claude-code'):
 payload=json.loads((examples/('public-source-'+frontend+'.example.json')).read_text())
 f=Fixture();register(f,payload,expect_failure=True);assert not f.writes and not f.reads
 payload['redirectUris']=['http://127.0.0.1:19483/client-supplied-fixture-callback']
 register(f,payload);register(f,payload)
 assert sum(w[0]=='POST' and w[1]=='/clients' for w in f.writes)==1 and not f.client_puts()
 before=len(f.writes);payload['redirectUris']=['http://localhost:19483/different-fixture-callback']
 register(f,payload,expect_failure=True);assert len(f.writes)==before
 payload['redirectUris']=['http://localhost:19483/*'];before=len(f.reads)
 register(f,payload,expect_failure=True);assert len(f.reads)==before
print('PASS: both public frontend templates reject placeholders/wildcards, POST once, no-op on repeat and refuse existing redirect mismatches without PUT.')

unsafe_registration_callbacks=(None,42,'','/callback','http://source.invalid/callback','https://',
 'https://OFFLINE_URI_USER:OFFLINE_URI_PASSWORD@source.invalid/callback',
 'https://source.invalid:70000/callback','https://source.invalid:invalid/callback',
 'https://source.invalid:0/callback','https://source.invalid:/callback',
 'https://source.invalid/callback#fragment','https://source.invalid/callback#',
 'https://source.invalid/callback%0A','https://source.invalid/call\nback',
 ' https://source.invalid/callback','https://source.invalid/callback ',
 'https://source.invalid/callback%20','https://source.invalid/callback%7F',
 'https://source.invalid/%2A','https://source.invalid/%3Ccallback%3E',
 'https://source.invalid/callback%5Cextra','https://source%2einvalid/callback')
for uri in unsafe_registration_callbacks:
 payload=json.loads((examples/'public-source-codex.example.json').read_text())
 payload['redirectUris']=[uri];f=Fixture()
 register(f,payload,expect_failure=True);assert not f.reads and not f.writes
payload=json.loads((examples/'public-source-codex.example.json').read_text())
callbacks=['https://source.invalid/exact-callback?state=fixture&flow=code',
 'http://127.0.0.1:19483/callback?state=fixture','http://localhost:19483/callback',
 'http://[::1]:19483/callback','https://[2001:db8::1]:9443/callback']
payload['redirectUris']=callbacks;f=Fixture();register(f,payload);register(f,payload)
assert len(f.writes)==1 and f.clients[payload['clientId']]['redirectUris']==callbacks
c=config('standard');c['sourceClientIds']=[payload['clientId']];run(f,c)
assert f.clients[payload['clientId']]['redirectUris']==callbacks
print('PASS: unsafe new-client callbacks fail before all reads/writes; exact HTTPS/loopback callbacks including queries remain unchanged and pass configuration.')

for key in ('oauth2.device.authorization.grant.enabled','oidc.ciba.grant.enabled','oauth2.jwt.authorization.grant.enabled'):
 payload=json.loads((examples/'public-source-codex.example.json').read_text())
 payload['redirectUris']=['http://127.0.0.1:19483/fixture-callback']
 f=Fixture();register(f,payload)
 existing=f.clients[payload['clientId']]
 existing['attributes'].update({'keep':'yes',key:'false'})
 before=copy.deepcopy(existing);writes=copy.deepcopy(f.writes)
 register(f,payload);assert existing==before and f.writes==writes
 for enabled in ('true','TrUe',True):
  existing['attributes'][key]=enabled;before=copy.deepcopy(existing)
  register(f,payload,expect_failure=True)
  assert existing==before and f.writes==writes,key+' grant was silently changed'
print('PASS: existing public registrations allow unrelated/disabled attributes and reject enabled device, CIBA and JWT grants without mutation.')

# Execute the token probe against intercepted token responses in both modes.
probe_code=(root/'exchange-token-probe.sh').read_text().split("<<'PY'\n",1)[1].rsplit('\nPY',1)[0]
MISSING_SUBJECT=object()
ACTOR_SUBJECT='OFFLINE_ACTOR_SUBJECT_SENTINEL'
def probe_subjects(mode,source_subject,exchanged_subject,source_actor=ACTOR_SUBJECT,exchanged_actor=ACTOR_SUBJECT):
 c=config(mode)
 a={'iss':c['issuer'],'exp':int(time.time())+300,'aud':[c['resource'],'ilm-mcp'],'scope':'mcp:access'}
 b={'iss':c['issuer'],'exp':int(time.time())+300,'aud':['ilm'],'azp':'ilm-mcp',
  'preferred_username':'OFFLINE_USERNAME_SENTINEL'}
 if source_subject is not MISSING_SUBJECT:a['sub']=source_subject
 if exchanged_subject is not MISSING_SUBJECT:b['sub']=exchanged_subject
 if mode=='delegation':
  a['may_act']={'client_id':'ilm-mcp'};b['act']={'client_id':'ilm-mcp'}
  if source_actor is not MISSING_SUBJECT:a['may_act']['sub']=source_actor
  if exchanged_actor is not MISSING_SUBJECT:b['act']['sub']=exchanged_actor
 def jwt(claims):return 'fixture.'+base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=')+'.fixture'
 source=jwt(a);exchanged=jwt(b);actor=jwt({} if source_actor is MISSING_SUBJECT else {'sub':source_actor});requests=[]
 class Tokens:
  def open(self,request,timeout):
   assert request.full_url==c['issuer']+'/protocol/openid-connect/token' and request.get_method()=='POST'
   fields=urllib.parse.parse_qs(request.data.decode());grant=fields['grant_type'][0];requests.append(grant)
   assert fields['client_id']==['ilm-mcp'] and fields['client_secret']==[SECRET]
   if grant=='client_credentials':
    assert mode=='delegation' and len(requests)==1
    return Response({'access_token':actor,'token_type':'Bearer'})
   assert grant=='urn:ietf:params:oauth:grant-type:token-exchange'
   assert fields['subject_token']==[source] and fields['audience']==['ilm']
   assert fields.get('actor_token')==([actor] if mode=='delegation' else None)
   return Response({'access_token':exchanged,'token_type':'Bearer'})
 with tempfile.TemporaryDirectory() as d:
  d=pathlib.Path(d);cf=d/'config';cf.write_text(json.dumps(c))
  secret=d/'secret';secret.write_text(SECRET);secret.chmod(0o600)
  token=d/'source';token.write_text(source);token.chmod(0o600)
  out=d/'responses';out.mkdir(mode=0o700)
  env={'MCP_SECRET_FILE':str(secret),'SOURCE_TOKEN_FILE':str(token),'PROBE_OUTPUT_DIR':str(out)}
  oldenv={k:os.environ.get(k) for k in env};old=urllib.request.build_opener;oldargv=sys.argv
  os.environ.update(env);urllib.request.build_opener=lambda *args:Tokens();sys.argv=['-',str(cf)]
  output=io.StringIO();errors=io.StringIO();failure=None
  try:
   with contextlib.redirect_stdout(output),contextlib.redirect_stderr(errors):
    try:exec(compile(probe_code,'exchange-token-probe.sh','exec'),{})
    except SystemExit as error:
     if error.code not in (None,0):failure=str(error)
  finally:
   urllib.request.build_opener=old;sys.argv=oldargv
   for key,value in oldenv.items():
    if value is None:os.environ.pop(key,None)
    else:os.environ[key]=value
  files=sorted(path.name for path in out.iterdir())
  assert all(path.stat().st_mode & 0o777==0o600 for path in out.iterdir())
  text=output.getvalue()+errors.getvalue()+(failure or '')
  assert not errors.getvalue()
  assert not any(value in text for value in (SECRET,source,exchanged,actor,ACTOR_SUBJECT,'OFFLINE_OTHER_ACTOR_SENTINEL',
   'OFFLINE_USERNAME_SENTINEL','OFFLINE_USER_SUBJECT_SENTINEL','OFFLINE_OTHER_SUBJECT_SENTINEL',json.dumps(b)))
  return failure,requests,files,output.getvalue()

invalid_subjects=(('missing',MISSING_SUBJECT),('null',None),('empty',''),('blank',' \t\n'),
 ('number',42),('boolean',True),('array',[]),('object',{}))
for selected_mode in ('standard','delegation'):
 for name,invalid in invalid_subjects:
  failure,requests,files,output=probe_subjects(selected_mode,invalid,invalid)
  assert failure=='A subject check failed.',(selected_mode,name,'invalid source subject falsely reported PASS')
  assert not requests and not files and not output,(selected_mode,name,'invalid source subject caused side effects')
 expected_requests=(['client_credentials'] if selected_mode=='delegation' else [])+['urn:ietf:params:oauth:grant-type:token-exchange']
 expected_files=(['actor.json'] if selected_mode=='delegation' else [])+['exchanged.json']
 for name,invalid in (*invalid_subjects,('mismatch','OFFLINE_OTHER_SUBJECT_SENTINEL')):
  failure,requests,files,output=probe_subjects(selected_mode,'OFFLINE_USER_SUBJECT_SENTINEL',invalid)
  assert failure=='B subject/requester check failed.',(selected_mode,name,'invalid exchanged subject accepted')
  assert requests==expected_requests and files==expected_files and not output
 for subject in ('OFFLINE_USER_SUBJECT_SENTINEL','  OFFLINE_USER_SUBJECT_SENTINEL  '):
  failure,requests,files,output=probe_subjects(selected_mode,subject,subject)
  assert failure is None and output.startswith('PASS:') and requests==expected_requests and files==expected_files
 failure,requests,files,output=probe_subjects(selected_mode,'  OFFLINE_USER_SUBJECT_SENTINEL  ','OFFLINE_USER_SUBJECT_SENTINEL')
 assert failure=='B subject/requester check failed.' and not output
 assert requests==expected_requests and files==expected_files
print('PASS: token probes reject invalid source subjects before requests/files, require valid exact exchanged subjects, preserve opaque identifiers and suppress credentials/tokens in both modes.')

user_subject='OFFLINE_USER_SUBJECT_SENTINEL'
for name,invalid in invalid_subjects:
 failure,requests,files,output=probe_subjects('delegation',user_subject,user_subject,source_actor=invalid,exchanged_actor=invalid)
 assert failure and failure.startswith('A lacks permitted may_act actor.'),('delegation',name,'invalid permitted actor falsely reported PASS')
 assert not requests and not files and not output
actor_requests=['client_credentials','urn:ietf:params:oauth:grant-type:token-exchange'];actor_files=['actor.json','exchanged.json']
for name,invalid in (*invalid_subjects,('mismatch','OFFLINE_OTHER_ACTOR_SENTINEL')):
 failure,requests,files,output=probe_subjects('delegation',user_subject,user_subject,exchanged_actor=invalid)
 assert failure=='B act attribution check failed.',('delegation',name,'invalid exchanged actor accepted')
 assert requests==actor_requests and files==actor_files and not output
for actor_subject in (ACTOR_SUBJECT,'  '+ACTOR_SUBJECT+'  '):
 failure,requests,files,output=probe_subjects('delegation',user_subject,user_subject,source_actor=actor_subject,exchanged_actor=actor_subject)
 assert failure is None and output.startswith('PASS:') and requests==actor_requests and files==actor_files
failure,requests,files,output=probe_subjects('delegation',user_subject,user_subject,source_actor='  '+ACTOR_SUBJECT+'  ')
assert failure=='B act attribution check failed.' and not output and requests==actor_requests and files==actor_files
print('PASS: delegation probes require nonblank string actor identities before exchange and exact downstream attribution without normalization or sensitive output.')

# Protocol failures can contain reflected credentials before an HTTP response
# exists or while its body is read. Every helper must suppress both paths.
class BrokenHttp:
 def __init__(self,stage,reflected):self.stage=stage;self.reflected=reflected
 def open(self,request,timeout):
  if self.stage=='status':raise http.client.BadStatusLine(self.reflected)
  return self
 def __enter__(self):return self
 def __exit__(self,*args):pass
 def read(self):raise http.client.IncompleteRead(self.reflected.encode(),1)

def helper_http_failure(name,stage):
 helper_code=(root/name).read_text().split("<<'PY'\n",1)[1].rsplit('\nPY',1)[0]
 with tempfile.TemporaryDirectory() as d:
  d=pathlib.Path(d);c=config('standard');c['ilmProviderName']='fixture-provider'
  if name=='update-ilm-provider.sh':c['mode']='delegation'
  cf=d/'config';cf.write_text(json.dumps(c))
  token=d/'token';token.write_text('OFFLINE_ADMIN_TOKEN_SENTINEL');token.chmod(0o600)
  secret=d/'secret';secret.write_text(SECRET);secret.chmod(0o600)
  claims={'iss':c['issuer'],'exp':int(time.time())+300,'aud':[c['resource'],'ilm-mcp'],
   'scope':'mcp:access','sub':'OFFLINE_SOURCE_TOKEN_SENTINEL'}
  source=d/'source';source.write_text('fixture.'+base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=')+'.fixture');source.chmod(0o600)
  payload=json.loads((examples/'public-source-codex.example.json').read_text())
  payload['redirectUris']=['http://127.0.0.1:19483/fixture-callback']
  pf=d/'public';pf.write_text(json.dumps(payload))
  sentinels=(SECRET,ROTATED,REG,token.read_text(),claims['sub'])
  fixture=BrokenHttp(stage,' '.join(sentinels))
  argv=['-',str(cf)]
  if name=='configure-keycloak.sh':argv=['-','inspect',str(cf)]
  elif name=='read-mcp-secret.sh':argv.append(str(d/'saved-secret'))
  elif name=='register-public-source-client.sh':argv.append(str(pf))
  env={'KC_ADMIN_TOKEN_FILE':str(token),'ILM_ADMIN_TOKEN_FILE':str(token),
   'MCP_SECRET_FILE':str(secret),'SOURCE_TOKEN_FILE':str(source),'PROBE_OUTPUT_DIR':str(d)}
  oldenv={k:os.environ.get(k) for k in env};old=urllib.request.build_opener;oldargv=sys.argv
  os.environ.update(env);urllib.request.build_opener=lambda *a:fixture;sys.argv=argv
  output=io.StringIO();errors=io.StringIO();failure=None
  try:
   with contextlib.redirect_stdout(output),contextlib.redirect_stderr(errors):
    try:exec(compile(helper_code,name,'exec'),{})
    except SystemExit as e:
     if e.code not in (None,0):failure=str(e)
    except http.client.HTTPException:traceback.print_exc()
  finally:
   urllib.request.build_opener=old;sys.argv=oldargv
   for k,v in oldenv.items():
    if v is None:os.environ.pop(k,None)
    else:os.environ[k]=v
  text=output.getvalue()+errors.getvalue()+(failure or '')
  return bool(failure and 'details suppressed' in failure and not output.getvalue() and
   not errors.getvalue() and not any(value in text for value in sentinels))

unsafe_http=[]
for name in ('configure-keycloak.sh','exchange-token-probe.sh','read-mcp-secret.sh','register-public-source-client.sh','update-ilm-provider.sh'):
 for stage in ('status','body'):
  if not helper_http_failure(name,stage):unsafe_http.append(name+':'+stage)
assert not unsafe_http,'Unsanitized HTTP protocol failures: '+', '.join(unsafe_http)
print('PASS: all five helpers suppress reflected token/secret sentinels in stdout, stderr and fixed diagnostics for malformed HTTP status and truncated bodies.')
print('OFFLINE FIXTURES COMPLETE. No live network, real credentials, server acceptance or deployment validation performed.')
PY
