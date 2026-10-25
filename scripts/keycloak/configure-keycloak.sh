#!/usr/bin/env bash
# Public configuration only. Token is read from a private external file, never arguments.
set -euo pipefail
set +x
umask 077
python3 - "$@" <<'PY'
import http.client, ipaddress, json, os, pathlib, re, stat, sys, urllib.error, urllib.parse, urllib.request

def fail(message):
    raise SystemExit(message)
if len(sys.argv) != 3 or sys.argv[1] not in ('inspect','apply'):
    fail('Usage: KC_ADMIN_TOKEN_FILE=/private/path/token ./scripts/keycloak/configure-keycloak.sh inspect|apply config.json')
mode, config_path = sys.argv[1:]
cfg = json.loads(pathlib.Path(config_path).read_text())
q = lambda s: urllib.parse.quote(s, safe='')
for key in ('keycloakBaseUrl','realm','issuer'):
    if not cfg.get(key) or 'REQUIRED_' in cfg[key]: fail('Set '+key+' in configuration first.')
base = cfg['keycloakBaseUrl'].rstrip('/')
if urllib.parse.urlsplit(base).scheme != 'https': fail('HTTPS Keycloak URL required.')

def safe_exact_resource(uri):
    if not isinstance(uri,str) or not uri or not uri.isascii(): return False
    decoded=urllib.parse.unquote(uri)
    if ('REQUIRED_' in decoded.upper() or '?' in uri or '#' in uri or
            any(ch in decoded for ch in '*\\<>{}') or
            any(ch.isspace() or ord(ch)<32 or 127<=ord(ch)<=159 for ch in decoded) or
            re.search(r'%(?![0-9a-fA-F]{2})',uri)):
        return False
    try:
        parsed=urllib.parse.urlsplit(uri)
        host=parsed.hostname
        if (parsed.scheme!='https' or not host or '%' in host or
                parsed.username is not None or parsed.password is not None or
                parsed.port==0 or parsed.netloc.endswith(':')):
            return False
        if not re.fullmatch(r"(?:[a-zA-Z0-9._~!$&'()+,;=:@/-]|%[0-9a-fA-F]{2})*",parsed.path): return False
        if ':' in host:
            ipaddress.IPv6Address(host)
            return re.fullmatch(r'\[[0-9a-fA-F:.]+\](?::[0-9]+)?',parsed.netloc) is not None
        if not re.fullmatch(r'[a-zA-Z0-9.-]+(?::[0-9]+)?',parsed.netloc): return False
        host=host[:-1] if host.endswith('.') else host
        if re.fullmatch(r'[0-9.]+',host):
            ipaddress.IPv4Address(host)
            return True
        return len(host)<=253 and all(re.fullmatch(r'[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?',label)
                                     for label in host.split('.'))
    except ValueError:
        return False

resource=cfg.get('resource')
if (not safe_exact_resource(resource) or
        resource in (cfg.get('apiClientId','ilm'),cfg.get('actorClientId','ilm-mcp')) or
        resource in cfg.get('sourceClientIds',[])):
    fail('Set resource to an exact HTTPS URI with a valid host/port, no placeholders, userinfo, query, '
         'fragment, backslash, whitespace or controls, distinct from the API, actor and source client IDs.')

p = pathlib.Path(os.environ['KC_ADMIN_TOKEN_FILE'])
if not p.is_file() or p.stat().st_mode & 0o077: fail('Admin token file must have permissions 0600 or stricter.')
token = p.read_text().strip()
if not token or '\n' in token or '\r' in token: fail('Token file must contain only the raw bearer token.')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None
opener = urllib.request.build_opener(NoRedirect)
admin = '/admin/realms/'+q(cfg['realm'])

# Keycloak 26.8.1 ClientSecretConstants. ClientResource.update can clear these
# attributes even when the primary secret is omitted. Keep presence flags only.
ROTATION_STATE_KEYS = (
    'client.secret.rotation.enabled',
    'client.secret.expiration.time',
    'client.secret.remaining.expiration.time',
    'client.secret.rotated',
    'client.secret.rotated.creation.time',
    'client.secret.rotated.expiration.time',
)
rotation_state = {}

def discard_client_credentials(value):
    # A privileged list/full client GET can return secrets. Discard them before
    # any response becomes a configuration object, inspection item or PUT body.
    rows = value if isinstance(value, list) else [value]
    for row in rows:
        if not isinstance(row, dict): continue
        attributes = row.get('attributes') or {}
        present = {key for key in ROTATION_STATE_KEYS if attributes.get(key) not in (None, '')}
        if row.get('id'):
            rotation_state.setdefault(row['id'], set()).update(present)
        row.pop('secret', None)
        row.pop('registrationAccessToken', None)
        attributes.pop('client.secret.rotated', None)
    return value

def req(method, path, body=None, missing=False):
    data = None if body is None else json.dumps(body).encode()
    headers = {'Authorization':'Bearer '+token,'Accept':'application/json'}
    if data is not None: headers['Content-Type']='application/json'
    try:
        with opener.open(urllib.request.Request(base+path, data=data, headers=headers, method=method), timeout=30) as r:
            result=r.read()
            value = json.loads(result) if result else None
            clean_path = path.split('?', 1)[0]
            client_path = admin+'/clients'
            if clean_path == client_path or (clean_path.startswith(client_path+'/') and
                    '/' not in clean_path[len(client_path)+1:]):
                value = discard_client_credentials(value)
            return value
    except urllib.error.HTTPError as e:
        if missing and e.code==404: return None
        fail(f'{method} {path.split("?")[0]} failed with HTTP {e.code}; response body suppressed.')
    except (urllib.error.URLError,http.client.HTTPException):
        fail('Connection/TLS error; details suppressed.')

def get(path): return req('GET', admin+path)
def put(path, body=None): return req('PUT', admin+path, body)
def post(path, body): return req('POST', admin+path, body)
def client(cid, required=True, full=True):
    found=[x for x in get('/clients?'+urllib.parse.urlencode({'clientId':cid})) if x.get('clientId')==cid]
    if len(found)>1: fail('Ambiguous client ID '+cid)
    if not found:
        if required: fail('Existing client not found: '+cid)
        return None
    if not full:
        return {key:found[0][key] for key in ('id','clientId')}
    return get('/clients/'+q(found[0]['id']))
def assignments(cid):
    return {kind:get('/clients/'+q(cid)+'/'+kind+'-client-scopes') for kind in ('default','optional')}
def sanitized_mapper(m):
    return {k:v for k,v in m.items() if k not in ('id','secret','registrationAccessToken')}

# Keycloak 26.8.1 OAuth2DeviceConfig, CibaConfig and OIDCConfigAttributes.
NON_CODE_GRANT_ATTRIBUTES = (
    'oauth2.device.authorization.grant.enabled',
    'oidc.ciba.grant.enabled',
    'oauth2.jwt.authorization.grant.enabled',
)

def safe_exact_callback(uri):
    if not isinstance(uri,str) or not uri: return False
    decoded=urllib.parse.unquote(uri)
    if ('REQUIRED_' in uri or '#' in uri or any(ch in decoded for ch in '*\\<>{}') or
            any(ch.isspace() or ord(ch)<32 or ord(ch)==127 for ch in decoded)):
        return False
    try:
        parsed=urllib.parse.urlsplit(uri)
        port=parsed.port
        return bool(parsed.hostname and '%' not in parsed.hostname and not parsed.fragment and
            parsed.username is None and parsed.password is None and port!=0 and
            not parsed.netloc.endswith(':') and
            (parsed.scheme=='https' or (parsed.scheme=='http' and
                parsed.hostname in ('localhost','127.0.0.1','::1'))))
    except ValueError:
        return False

def inspect_callback(uri):
    # Do not echo embedded URI credentials even when inspecting an unsafe client.
    if not isinstance(uri,str): return '<invalid callback>'
    try:
        parsed=urllib.parse.urlsplit(uri)
        if parsed.username is not None or parsed.password is not None:
            return '<unsafe callback: embedded credentials redacted>'
    except ValueError:
        return '<invalid callback>'
    return uri

def validate_source_client(c):
    if (c.get('protocol')!='openid-connect' or c.get('enabled') is not True or
            c.get('bearerOnly') is not False or c.get('publicClient') is not True or
            c.get('standardFlowEnabled') is not True):
        fail('Source must be an enabled public OpenID Connect authorization-code client: '+c['clientId'])
    for flag in ('implicitFlowEnabled','directAccessGrantsEnabled','serviceAccountsEnabled'):
        if c.get(flag) is not False:
            fail('Source must have '+flag+' explicitly disabled; no flows changed: '+c['clientId'])
    attributes=c.get('attributes') or {}
    for name in NON_CODE_GRANT_ATTRIBUTES:
        if str(attributes.get(name,'false')).lower()=='true':
            fail('Source has non-code grant '+name+' enabled; no flows changed: '+c['clientId'])
    redirects=c.get('redirectUris')
    if not isinstance(redirects,list) or not redirects or not all(safe_exact_callback(uri) for uri in redirects):
        fail('Source requires nonempty exact HTTPS/loopback callbacks without wildcards, placeholders, '
             'userinfo or fragments; no redirects changed: '+c['clientId'])

realm=get('')
ilm=client(cfg.get('apiClientId','ilm'), full=False)
if cfg.get('apiClientId','ilm')!='ilm' or cfg.get('actorClientId','ilm-mcp')!='ilm-mcp': fail('This runbook requires apiClientId=ilm and actorClientId=ilm-mcp.')
scopes=get('/client-scopes')
scopes_by_name={s['name']:s for s in scopes}
ilm_assigned=assignments(ilm['id'])
if mode=='inspect':
    report={'realm':cfg['realm'],'adminPermissionsEnabled':realm.get('adminPermissionsEnabled'),
            'ilmClientUuid':ilm['id'],'ilmDirectMappers':[sanitized_mapper(m) for m in get('/clients/'+q(ilm['id'])+'/protocol-mappers/models')],
            'ilmAssignedScopes':{},'existingSourceClients':[]}
    for kind, rows in ilm_assigned.items():
        report['ilmAssignedScopes'][kind]=[]
        for row in rows:
            scope_id=q(row['id'])
            report['ilmAssignedScopes'][kind].append({'id':row['id'],'name':row['name'],
                'mappers':[sanitized_mapper(m) for m in get('/client-scopes/'+scope_id+'/protocol-mappers/models')],
                'roleScopeMappings':get('/client-scopes/'+scope_id+'/scope-mappings')})
    for cid in cfg.get('sourceClientIds',[]):
        c=client(cid)
        report['existingSourceClients'].append({'clientId':cid,'id':c['id'],'publicClient':c.get('publicClient'),
            'protocol':c.get('protocol'),'enabled':c.get('enabled'),'bearerOnly':c.get('bearerOnly'),
            'standardFlowEnabled':c.get('standardFlowEnabled'),'fullScopeAllowed':c.get('fullScopeAllowed'),
            'implicitFlowEnabled':c.get('implicitFlowEnabled'),'directAccessGrantsEnabled':c.get('directAccessGrantsEnabled'),
            'serviceAccountsEnabled':c.get('serviceAccountsEnabled'),
            'grantAttributes':{name:(c.get('attributes') or {}).get(name) for name in NON_CODE_GRANT_ATTRIBUTES},
            'redirectUris':[inspect_callback(uri) for uri in (c.get('redirectUris') or [])],
            'pkceCodeChallengeMethod':(c.get('attributes') or {}).get('pkce.code.challenge.method'),
            'consentRequired':c.get('consentRequired'),'assignedScopes':assignments(c['id'])})
    # Public schema/configuration only; no realm export or client secrets are emitted.
    print(json.dumps(report,indent=2)); sys.exit(0)

exchange_mode=cfg.get('mode')
if exchange_mode not in ('delegation','standard'): fail('mode must be delegation or standard.')
if not cfg.get('sourceClientIds'): fail('Select existing public sourceClientIds first.')
if not cfg.get('mappingSelectionReviewed') or not cfg.get('sourceAudienceConfigurationReviewed'):
    fail('Inspect existing username/role mappings and source audiences, then set both review flags true.')
if not cfg.get('usernameClaim') or cfg['usernameClaim'].startswith('REQUIRED_'): fail('Set existing ILM username claim.')
if exchange_mode=='delegation' and 'delegation:client' not in scopes_by_name:
    fail('Missing built-in delegation:client scope: enable server token-exchange-delegation and parameterized-scopes first.')
# Validate all selections before any mutation. Never creates roles or grants roles to users.
ilm_attached={s['name']:s for rows in ilm_assigned.values() for s in rows}
for name in cfg.get('targetScopeNames',[]):
    if name not in ilm_attached: fail('Selected target scope is not currently attached to ilm: '+name)
    if name.startswith('delegation:') or name=='mcp:access': fail('Invalid target mapping scope '+name)
ilm_mappers=get('/clients/'+q(ilm['id'])+'/protocol-mappers/models')
copy_mappers=[]
for name in cfg.get('copyIlmDirectMapperNames',[]):
    rows=[m for m in ilm_mappers if m['name']==name]
    if len(rows)!=1: fail('Missing/ambiguous ILM mapper: '+name)
    copy_mappers.append(sanitized_mapper(rows[0]))
if len({m['name'] for m in copy_mappers}) != len(copy_mappers) or any(m['name']=='ilm-api-audience' for m in copy_mappers):
    fail('Selected mapper names collide with dedicated upstream mapper names.')
client_roles=[get('/clients/'+q(ilm['id'])+'/roles/'+q(name)) for name in cfg.get('ilmClientRoleNames',[])]
realm_roles=[get('/roles/'+q(name)) for name in cfg.get('realmRoleNames',[])]
sources=[]
for cid in cfg['sourceClientIds']:
    if cid in ('ilm','ilm-mcp'): fail('Source client must be a separate existing public OAuth client.')
    c=client(cid)
    validate_source_client(c)
    sources.append(c)
# Explicit member IDs are additions only; an empty list keeps a new group empty.
for uid in cfg.get('selectedGroupMemberUserIds',[]):
    u=get('/users/'+q(uid))
    if u.get('serviceAccountClientId'): fail('Select human users only, no service accounts.')

actor=client('ilm-mcp',required=False)
actor_patch={'clientId':'ilm-mcp','protocol':'openid-connect','enabled':True,'publicClient':False,
    'bearerOnly':False,'clientAuthenticatorType':'client-secret','standardFlowEnabled':False,
    'implicitFlowEnabled':False,'directAccessGrantsEnabled':False,
    'fullScopeAllowed':False,'consentRequired':False}
if actor is None or exchange_mode=='delegation':
    actor_patch['serviceAccountsEnabled']=exchange_mode=='delegation'
# An existing service account is retained in standard mode. Setting false would
# delete its user and break the identity pinned in existing delegation tokens.
actor_patch['attributes']={**(actor or {}).get('attributes',{}),
    'standard.token.exchange.enabled':'true','standard.token.exchange.enableRefreshRequestedTokenType':'NO'}

def changed(existing, patch):
    return any(existing.get(key) != value for key,value in patch.items())

source_updates=[]
for c in sources:
    patch={'fullScopeAllowed':False,
        'attributes':{**c.get('attributes',{}),'pkce.code.challenge.method':'S256'}}
    if exchange_mode=='delegation': patch['consentRequired']=True
    if changed(c, patch): source_updates.append((c,{**c,**patch}))
actor_needs_put=actor is not None and changed(actor,actor_patch)
# Complete this check for EVERY existing client needing a PUT before creating
# even a scope/group/client. Never restore or replay private rotation metadata.
for c,_ in source_updates+([(actor,None)] if actor_needs_put else []):
    if rotation_state.get(c['id']):
        fail('Client '+c['clientId']+' needs a PUT but has credential-rotation state; '
             'aborted before any mutation. Reconcile through an isolated 26.8.1 '
             'client-policy acceptance workflow, then rerun. No rotation values printed.')

if actor is None:
    post('/clients',actor_patch)
    actor=client('ilm-mcp')
elif actor_needs_put:
    put('/clients/'+q(actor['id']),{**actor,**actor_patch})
actor_id=q(actor['id'])

def ensure_scope(name, mapper_list):
    rows=[s for s in get('/client-scopes') if s['name']==name]
    if not rows:
        post('/client-scopes',{'name':name,'protocol':'openid-connect',
            'description':'Shared ILM MCP; managed by this runbook',
            'attributes':{'include.in.token.scope':'true','display.on.consent.screen':'true'}})
        rows=[s for s in get('/client-scopes') if s['name']==name]
    if len(rows)!=1: fail('Ambiguous client scope '+name)
    s=rows[0]
    if s.get('protocol')!='openid-connect': fail('Existing scope protocol mismatch: '+name)
    path='/client-scopes/'+q(s['id'])
    existing=get(path+'/protocol-mappers/models')
    wanted={m['name']:m for m in mapper_list}
    # Dedicated scope collision fails closed instead of altering unknown existing mappers.
    if any(m['name'] not in wanted for m in existing): fail('Unmanaged extra mapper in dedicated scope '+name)
    for m in mapper_list:
        found=[x for x in existing if x['name']==m['name']]
        if found:
            if sanitized_mapper(found[0])!=m: fail('Existing dedicated mapper differs: '+m['name'])
        else: post(path+'/protocol-mappers/models',m)
    return s

def aud_mapper(name,key,value):
    return {'name':name,'protocol':'openid-connect','protocolMapper':'oidc-audience-mapper',
        'consentRequired':False,'config':{key:value,'access.token.claim':'true','id.token.claim':'false','introspection.token.claim':'true'}}
source_scope=ensure_scope('mcp:access',[
    aud_mapper('mcp-public-resource','included.custom.audience',cfg['resource']),
    aud_mapper('mcp-exchange-requester','included.client.audience','ilm-mcp')])
upstream_scope=ensure_scope('ilm-mcp-upstream',[
    aud_mapper('ilm-api-audience','included.client.audience','ilm'),*copy_mappers])
upstream_path='/client-scopes/'+q(upstream_scope['id'])
if client_roles: post(upstream_path+'/scope-mappings/clients/'+q(ilm['id']),client_roles)
if realm_roles: post(upstream_path+'/scope-mappings/realm',realm_roles)
put('/clients/'+actor_id+'/optional-client-scopes/'+q(upstream_scope['id']))
for name in cfg.get('targetScopeNames',[]):
    put('/clients/'+actor_id+'/default-client-scopes/'+q(ilm_attached[name]['id']))
for c,patch in source_updates:
    put('/clients/'+q(c['id']),patch)
for c in sources:
    put('/clients/'+q(c['id'])+'/optional-client-scopes/'+q(source_scope['id']))

if exchange_mode=='delegation':
    # Realm representation update is partial; do not import/reset a realm.
    if not realm.get('adminPermissionsEnabled'): put('',{'adminPermissionsEnabled':True})
    admin_client=client('admin-permissions', full=False)
    authz='/clients/'+q(admin_client['id'])+'/authz/resource-server'
    # Source-confirmed FGAP V2 route and typed representations, not V1 management permissions.
    def ensure_typed(path, body):
        # Search uses exact name and is handled by the generic policy service.
        existing=req('GET',admin+path+'/search?'+urllib.parse.urlencode({'name':body['name']}),missing=True)
        if existing is None: return post(path+'/'+body['type'],body)
        if existing.get('type')!=body['type']: fail('Existing authz object type differs: '+body['name'])
        put(path+'/'+body['type']+'/'+q(existing['id']),{**body,'id':existing['id']})
        return body
    groups=get('/groups?'+urllib.parse.urlencode({'search':'ilm-mcp-users','exact':'true','max':100}))
    exact=[g for g in groups if g.get('name')=='ilm-mcp-users' and g.get('path')=='/ilm-mcp-users']
    if len(exact)>1: fail('Ambiguous top-level group ilm-mcp-users.')
    if not exact:
        post('/groups',{'name':'ilm-mcp-users'})
        exact=[g for g in get('/groups?'+urllib.parse.urlencode({'search':'ilm-mcp-users','exact':'true','max':100})) if g.get('path')=='/ilm-mcp-users']
    if len(exact)!=1: fail('Cannot resolve ilm-mcp-users group.')
    group=exact[0]
    policy={'name':'ilm-mcp-delegating-client','type':'client','logic':'POSITIVE','decisionStrategy':'UNANIMOUS','clients':[actor['id']]}
    ensure_typed(authz+'/policy',policy)
    permission={'name':'ilm-mcp-selected-users-delegation','type':'scope','logic':'POSITIVE','decisionStrategy':'UNANIMOUS',
        'resourceType':'Groups','resources':[group['id']],'scopes':['delegate-members'],'policies':[policy['name']]}
    ensure_typed(authz+'/permission',permission)
    # PUT membership is idempotent and leaves all other members and groups untouched.
    for uid in cfg.get('selectedGroupMemberUserIds',[]): put('/users/'+q(uid)+'/groups/'+q(group['id']))
    service_account=get('/clients/'+actor_id+'/service-account-user')
    print('Configured delegation; group UUID='+group['id']+'; actor service-account UUID='+service_account['id'])
print('Configuration applied. Privileged GETs may have returned credentials; they were discarded from configuration objects and omitted from output/PUT. Run token probes and real ILM authorization checks before deployment.')
PY
