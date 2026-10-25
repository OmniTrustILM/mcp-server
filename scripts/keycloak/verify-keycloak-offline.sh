#!/usr/bin/env bash
# Offline fixture only. All HTTP is intercepted; no live network/credentials are used.
set -euo pipefail
set +x
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
python3 - "$SCRIPT_DIR" <<'PY'
import ast,contextlib,copy,io,json,os,pathlib,sys,tempfile,urllib.parse,urllib.request
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
  assert not any(v in output.getvalue()+(failure or '')+json.dumps(f.writes) for v in (SECRET,ROTATED,REG))
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
print('OFFLINE FIXTURES COMPLETE. No live network, real credentials, server acceptance or deployment validation performed.')
PY
