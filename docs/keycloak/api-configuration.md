# Example Keycloak 26.8.1 API integration

This provider-specific runbook targets Keycloak 26.8.1 in an operator-selected existing realm. Start with the [provider-neutral shared OAuth workflow](../shared-http-authentication.md). It requires Python 3, HTTPS access, selected exact callbacks/mappings and administrator credentials supplied through private files. Required source scopes are `openid mcp:access delegation:client:ilm-mcp` for explicit actor delegation, or `openid mcp:access` for ordinary Standard V2. Offline safety checks are available in `scripts/keycloak/verify-keycloak-offline.sh`; they do not establish server/deployment acceptance.

The default target is explicit client delegation: user token **A** authorizes `ilm-mcp`; `ilm-mcp` exchanges A plus its own service-account token for **B**, which ILM accepts for the same user. Existing ILM issuer/provider/client ID `ilm` remains in use. There is no Core token issuer and no second provider with the same issuer.

| Token | Audience and identity contract |
| --- | --- |
| A, presented to MCP | Exact existing issuer; `aud` contains `https://ilm.example.invalid/mcp` **and** `ilm-mcp`; `aud` excludes `ilm`; scope contains `mcp:access`. In delegation mode `may_act.client_id=ilm-mcp`, and `may_act.sub` is its service-account UUID. |
| Actor token, internal only | Obtained by `ilm-mcp` client credentials. Its service-account identity is the actor, never the ILM end user. |
| B, presented only to ILM | Same user `sub`; `aud=ilm`; `azp=ilm-mcp`; existing ILM username and role mappings; `act.client_id=ilm-mcp`, `act.sub` equal to the service-account UUID. No public resource audience and no `may_act`. |

The reserved example `https://ilm.example.invalid/mcp` represents the public protected-resource identifier; replace it with the deployment's exact HTTPS resource URL. It is distinct from the Keycloak requester client ID `ilm-mcp`. Standard V2 requires the requester client ID in A's audience; a public URL alone does not satisfy that check. The script therefore configures two audience mappers in `mcp:access`. The exchange's `audience=ilm` filters already available target audiences; the dedicated upstream scope adds the ILM audience before that filtering.

For the execution sequence, start with [the compact operator quickstart](README.md). This document supplies the detailed constraints and evidence.

## Inputs that must be supplied

Copy `keycloak-input.example.json` to a private working directory and fill in:

- `keycloakBaseUrl`: exact Keycloak origin and optional deployment prefix, excluding `/admin/realms/...` and `/realms/...`. A reserved example is `https://identity.example.invalid` with issuer `https://identity.example.invalid/realms/example` for realm `example`; use discovery to verify the actual issuer.
- `realm` and `issuer`: exact existing realm name and issuer. Compare discovery `issuer` to the existing ILM provider's `issuerUrl`; do not guess a hostname or add/remove a trailing slash from the issuer value.
- `sourceClientIds`: **public** OAuth authorization-code frontend client registrations actually used by the MCP host. Reuse reviewed dedicated registrations, or create them with `register-public-source-client.sh` and the supplied Codex/Claude Code payload templates below. Exact redirects come from that frontend client; configuration preserves them. No dynamic registration or guessed desktop callback is introduced.
- `usernameClaim`: the existing ILM provider's username claim, with the platform's effective default applied if it is unset. Confirm it in the existing deployment rather than assuming it equals `sub`.
- `targetScopeNames`: selected existing scopes attached to `ilm` that provide its expected username/roles. For a conventional configuration these may include `basic`, `profile`, and `roles`, but the actual selection must come from inspection. These are attached to `ilm-mcp` as defaults, using existing scope definitions.
- `copyIlmDirectMapperNames`: only required direct mappers on the existing `ilm` client that are absent from the selected shared scopes. The script copies their definitions to the dedicated `ilm-mcp-upstream` scope with new mapper UUIDs.
- `ilmClientRoleNames` and `realmRoleNames`: narrow lists of existing role names required for the ILM token. These add **role scope mappings**, never user/group role grants. Composite roles must be reviewed for inherited breadth. Existing user and group role assignments stay unchanged.
- `selectedGroupMemberUserIds`: explicit UUIDs of users the operator selected for MCP delegation. Empty by default. The new `/ilm-mcp-users` group is initially empty unless explicit UUIDs are supplied. A reused group keeps its existing members; the script adds only listed users and never replaces membership.
- `ilmApiBaseUrl` and `ilmProviderName`: exact existing ILM API base, including any `/api` prefix, and existing provider name. The example `/api` is an input to verify, not a claim about the live reverse proxy.

`mappingSelectionReviewed` and `sourceAudienceConfigurationReviewed` start false. Set them true only after reviewing the selected mappings and ensuring A cannot acquire ILM audiences through direct mappers, default client scopes, role scope mappings or composites. Full Scope Allowed is switched off on source clients and requester; any existing explicit source mapping that adds `ilm` still needs separate correction by the operator. The scripts deliberately do not delete unrelated source mappings. Actual A tokens must also pass the audience probe before deployment.

Every reused source must be an enabled public `openid-connect` client with `standardFlowEnabled=true`, `bearerOnly=false`, and explicit `false` values for `implicitFlowEnabled`, `directAccessGrantsEnabled` and `serviceAccountsEnabled`. Device, CIBA and JWT authorization grants must be disabled as well: their Keycloak 26.8.1 attributes are `oauth2.device.authorization.grant.enabled`, `oidc.ciba.grant.enabled` and `oauth2.jwt.authorization.grant.enabled`. These checks apply in **both** exchange modes and run before the first mutation. The helper rejects unsafe sources rather than silently disabling their existing flows.

Source callbacks must be a nonempty list of exact HTTPS URIs or loopback HTTP URIs (`localhost`, `127.0.0.1`, `::1`), with valid ports and no wildcard (including escaped wildcard), unresolved placeholder, relative URL, URI credentials, fragment, backslash or control/whitespace characters. Existing callbacks are never replaced by apply. Inspect exposes protocol/enabled/bearer/public settings, every listed flow flag, the three grant attributes, registered redirects and `pkceCodeChallengeMethod`; embedded URI credentials are redacted. It remains the operator's responsibility to match the exact callback emitted by the installed frontend. Apply can explicitly change an otherwise valid source's existing PKCE method to S256, as documented, after the rotation-state guard passes; it does not bypass that guard to do so.

Coordinate edits with other realm/provider administrators and scheduled credential-rotation jobs. This runbook uses read/merge/write and has no cross-service transaction, atomic rotation-state check or protection against a concurrent update. A partial failure is recoverable by fixing the reported prerequisite and rerunning; do not treat partial output as successful deployment.

## Private operator session

Use a terminal without session recording. Never enable shell tracing, HTTP verbose logging or CI command echoing. Keep tokens, authorization codes, PKCE verifiers and client secrets outside the repository and outside chat.

```bash
# Run from the MCP repository root.
set +x
umask 077
MCP_SETUP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ilm-mcp-setup.XXXXXX")"
chmod 700 "$MCP_SETUP_DIR"
trap 'rm -rf "$MCP_SETUP_DIR"' EXIT HUP INT TERM
cp docs/keycloak/keycloak-input.example.json "$MCP_SETUP_DIR/config.json"
# Edit config.json with public configuration only.

# Paste a short-lived, appropriately authorized raw Keycloak admin bearer token.
# read -s prevents echo; the token value does not enter shell history.
IFS= read -r -s MCP_KC_ADMIN_TOKEN
printf '%s' "$MCP_KC_ADMIN_TOKEN" > "$MCP_SETUP_DIR/keycloak-admin.token"
unset MCP_KC_ADMIN_TOKEN
export KC_ADMIN_TOKEN_FILE="$MCP_SETUP_DIR/keycloak-admin.token"

./scripts/keycloak/configure-keycloak.sh inspect "$MCP_SETUP_DIR/config.json" > "$MCP_SETUP_DIR/inspection.json"
# Inspect the private inspection.json locally, choose exact mapping lists, then set review flags.
```

Inspection reads the chosen realm, client lookup results, selected scope definitions and source-client metadata. It avoids a full `/clients/{uuid}` GET for unrelated `ilm` metadata and uses only `ilm`'s identifiers from the lookup result. **Privileged client lookup/full GET responses can nevertheless include the primary secret and rotated-secret attributes.** The request helper discards primary `secret`, `registrationAccessToken` and the rotated-secret value immediately before returning any client configuration object; it retains only rotation-presence flags separately for the guard. Transient HTTP response bytes can contain credentials in process memory; Python does not guarantee secure memory erasure. No raw response is logged or saved. Inspection output/PUT bodies exclude those values. Use the private inspection file and avoid process-memory/session recording. This is credential retrieval into transient process memory when the server includes it, even though no `/client-secret` GET is performed by inspection. Obtain the admin token through your existing admin authentication process; do not enable password grants or use an administrator password in a command.

## Create the public frontend registrations when absent

Three clients have different jobs. The existing `ilm` client/provider is the downstream API identity/audience. The new confidential `ilm-mcp` is the MCP server's exchange actor and holds the only new server secret. `ilm-mcp-codex` and `ilm-mcp-claude-code` are independent **public** frontend registrations for user authorization-code/PKCE login; they have no secret and never perform token exchange. Use only the frontends you actually run.

Copy the corresponding template into the private working directory and replace its callback placeholder:

```bash
cp docs/keycloak/public-source-codex.example.json "$MCP_SETUP_DIR/codex-client.json"
cp docs/keycloak/public-source-claude-code.example.json "$MCP_SETUP_DIR/claude-code-client.json"
# Set each redirectUris array to exact callback(s) from that installed client.
./scripts/keycloak/register-public-source-client.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/codex-client.json"
./scripts/keycloak/register-public-source-client.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/claude-code-client.json"
```

The helper uses `GET /clients?clientId=...` and **`POST /clients` only if absent**. It never PUTs an existing registration. A matching existing public registration is a no-op; any difference in the required flow/auth/redirect fields stops before mutation. It rejects wildcard redirects and credential-bearing/extra payload fields. Add the created client IDs to `sourceClientIds`, then rerun inspection/configuration. If only one frontend is wanted, run only that template/command.

The supplied `ClientRepresentation` templates specify `publicClient=true` (OAuth client authentication `none`), `standardFlowEnabled=true`, implicit/password/service-account flows false, Full Scope Allowed false, `consentRequired=true`, and `attributes["pkce.code.challenge.method"]="S256"`. Redirects contain no invented value. Leave consent enabled for delegation. The setup helper subsequently attaches the dedicated `mcp:access` scope; built-in `delegation:client` availability is checked, not manually reprovisioned.

For **Codex CLI**, the official command accepts the pre-registered public ID and displays the callback to register:

```bash
codex mcp add ilm --url https://ilm.example.invalid/mcp --oauth-client-id ilm-mcp-codex
```

Copy the complete displayed callback, including any server-specific path/ID. For exact port matching, configure `mcp_servers.ilm.oauth.callback_port` and a matching explicit callback URL/listener as documented, then verify the final `redirect_uri`. Issuer-response support affects callback-ID selection; never guess its suffix. [Official Codex MCP registration/callback documentation](https://learn.chatgpt.com/docs/extend/mcp#oauth-client-registration-and-callbacks). With delegation, Codex also needs its `scopes` set, which `codex mcp add` cannot do; the configuration entry is under [Connect clients](README.md#connect-clients).

For **Claude Code**, choose an available fixed local callback port and supply the public ID without a client secret:

```bash
claude mcp add --transport http --client-id ilm-mcp-claude-code \
  --callback-port "${MCP_CLAUDE_CALLBACK_PORT:?set your chosen available local callback port}" \
  ilm https://ilm.example.invalid/mcp
```

Register the exact callback for that installed version and chosen port. Current documentation uses `http://localhost:PORT/callback`; v2.1.229 used `127.0.0.1`, while v2.1.231 restored `localhost`. Verify rather than registering both by assumption. [Official Claude Code callback/public-client documentation](https://code.claude.com/docs/en/mcp#use-pre-configured-oauth-credentials).

These CLI callbacks are not assumed for desktop connectors. A desktop connector requires its actual documented/UI-provided callback and a distinct reviewed registration when appropriate. If the MCP discovery endpoint is not yet deployed, complete the realm/requester/mapping work and provide the final frontend callback once the chosen client can determine it; do not fabricate a callback to make registration pass.

In delegation deployment, MCP protected-resource/challenge metadata must lead clients to request `openid mcp:access delegation:client:ilm-mcp`. Check the actual authorization request. If the client overrides scopes, include all three explicitly; otherwise login can succeed without `may_act` and delegation must fail closed. This is separate from registering callback URIs. Configure the MCP server's authorization scopes to advertise that set.

## Server and client configuration

Full actor attribution is an explicit opt-in. For **delegation** mode, build and deploy a Keycloak 26.8.1 optimized image with both features enabled at build time, preserving all other enabled features:

```text
KC_FEATURES=<preserve existing enabled features>,token-exchange-delegation,parameterized-scopes
```

If there are no existing explicit features, use `KC_FEATURES=token-exchange-delegation,parameterized-scopes`. In 26.8.1 source both are preview features, and standard V2 and FGAP V2 are default features. Confirm neither was explicitly disabled. Do not enable legacy `token-exchange:v1`, use FGAP V1, or replace an existing feature list. An already optimized image may have these build-time features disabled; adding runtime environment values alone does not establish support. Build a feature-enabled image through the Keycloak deployment pipeline (for example, run `kc.sh build --features=<complete-preserved-feature-list>` during that image build), publish it to the operator's registry, and redeploy it. Do not assume the default Keycloak image used by an existing environment supports client delegation. Exact image names and restart commands depend on that deployment.

Feature initialization supplies the built-in parameterized scope `delegation:client`; its absence makes the script stop before provisioning. The source OAuth clients must require consent for this scope. The script sets `consentRequired=true` on selected source clients in delegation mode, so these clients will show consent according to Keycloak's scope behavior.

Run after completing the mapping review:

```bash
./scripts/keycloak/configure-keycloak.sh apply "$MCP_SETUP_DIR/config.json"
```

The script creates/reconciles the following dedicated configuration by exact name, using Admin REST. Privileged lookup/full GETs may return credentials; they are discarded from returned configuration objects and excluded from inspection output/PUTs. Existing `ilm` metadata/mappings are read, never changed. Unchanged top-level client representations are not PUT. An existing client requiring a PUT must pass the rotation-state preflight below before **any** mutation begins. No unconditional server-side credential-preservation claim is made.

| Configuration | Admin REST operation, relative to `/admin/realms/{realm}` |
| --- | --- |
| Confidential requester `ilm-mcp` | `GET /clients?clientId=ilm-mcp`, `POST /clients` if absent, otherwise merge `PUT /clients/{uuid}`. Client authentication enabled, Standard Token Exchange ON, direct password/implicit flow OFF, Full Scope Allowed OFF. Service account ON in delegation mode. Standard mode preserves an existing service-account setting/identity; only a new standard requester starts with it OFF. Standard authorization code flow defaults OFF on this requester; it is optional and unnecessary for a separate source-client flow. |
| Exchange attribute | `attributes["standard.token.exchange.enabled"]="true"`; refresh exchange disabled with `standard.token.exchange.enableRefreshRequestedTokenType="NO"`. |
| `mcp:access` | `POST /client-scopes` if absent; two `oidc-audience-mapper` definitions for custom public resource and client `ilm-mcp`; attach as optional scope to the selected source clients. |
| `ilm-mcp-upstream` | Dedicated optional requester scope, audience mapper for `ilm`, narrow selected existing ILM mapper copies and role scope mappings. |
| Existing mapping scopes | Idempotent `PUT /clients/{ilm-mcp-uuid}/default-client-scopes/{scope-uuid}` for explicitly selected existing scope names. |
| Source OAuth clients | Merge `PUT /clients/{uuid}` preserving redirect URIs and unrelated settings; Full Scope Allowed OFF, PKCE S256 enforced, consent ON for delegation. |
| FGAP V2, delegation only | Partial `PUT` to realm root with `{"adminPermissionsEnabled":true}` if necessary; resolve generated `admin-permissions` client by client ID. |
| Dedicated target group | `GET /groups?search=ilm-mcp-users&exact=true`, `POST /groups {"name":"ilm-mcp-users"}` if absent. Explicit membership additions use `PUT /users/{user-uuid}/groups/{group-uuid}`. No existing memberships or user role assignments are removed. |

The script never sends `serviceAccountsEnabled=false` for an existing requester when switching to standard mode. Existing delegation → standard → delegation keeps its service-account UUID, including existing mappings/consent identity. Standard token execution still obtains no actor token and sends no actor parameters.

New realms, realm imports, all-users delegation permissions, impersonation roles, service-account admin roles and ILM user provisioning are outside this script. A new `ilm-mcp-users` group has no roles. If reusing an existing group, review its current role mappings and membership before applying: adding a member inherits that group's existing roles.

## Credential-rotation preflight

Keycloak 26.8.1 can clear rotation state during `PUT /clients/{uuid}` even when `secret` is omitted. Before its first POST/PUT, the script computes every needed existing actor/source client update. It skips unchanged client PUTs and aborts **before any mutation** if any client needing PUT has a nonempty value for one of these exact `ClientSecretConstants` attributes:

```text
client.secret.rotation.enabled
client.secret.expiration.time
client.secret.remaining.expiration.time
client.secret.rotated
client.secret.rotated.creation.time
client.secret.rotated.expiration.time
```

The rotated-secret value is discarded immediately; only its presence is retained for this decision. The guard is conservative, including stale/nonempty metadata. `client.secret.creation.time` alone is not a rotation-window flag: the version-pinned reset method does not remove it, so its nonsecret timestamp is retained with unrelated attributes. A correctly configured client with rotation state can be used without an unnecessary client PUT; adding scope assignments uses separate endpoints.

If the guard aborts, no script mutation has occurred. Do not remove these attributes, reset expiration, rotate the secret, or replay a saved rotated secret to force the script through. Have the deployment operator reconcile the desired client change through a separately verified 26.8.1 client-policy/rotation workflow in an isolated acceptance environment, then rerun after the preflight condition is legitimately resolved. This guide does not automate that private credential lifecycle or claim acceptance of rotation-enabled client updates. Avoid concurrent rotation while applying: there is no atomic transaction spanning preflight and writes.

## Exact FGAP V2 API payloads

Let `P=/admin/realms/{realm}/clients/{admin-permissions-internal-uuid}/authz/resource-server`. The UUID here belongs to **`admin-permissions`**, not `realm-management` or `ilm-mcp`.

`POST P/policy/client`:

```json
{
  "name": "ilm-mcp-delegating-client",
  "type": "client",
  "logic": "POSITIVE",
  "decisionStrategy": "UNANIMOUS",
  "clients": ["<ilm-mcp-INTERNAL-CLIENT-UUID>"]
}
```

`POST P/permission/scope`:

```json
{
  "name": "ilm-mcp-selected-users-delegation",
  "type": "scope",
  "logic": "POSITIVE",
  "decisionStrategy": "UNANIMOUS",
  "resourceType": "Groups",
  "resources": ["<ilm-mcp-users-GROUP-UUID>"],
  "scopes": ["delegate-members"],
  "policies": ["ilm-mcp-delegating-client"]
}
```

The group UUID in `resources` is the existing Keycloak entity UUID. FGAP V2 resolves/creates its authorization resource through the admin-permissions schema; do not create an arbitrary authorization resource yourself. Empty `resources` means a broader type-level permission and must never be substituted. `scopes` accepts the scope name, and `policies` accepts the existing policy name as shown in the version-pinned tests.

The script searches exact policy names with `GET P/policy/search?name=...` or `GET P/permission/search?name=...` (missing returns 204), then uses typed update paths `PUT P/policy/client/{policy-id}` and `PUT P/permission/scope/{permission-id}`. Its dedicated named objects are reconciled; unrelated permissions remain unchanged. Audit existing broader FGAP policies independently because an additive narrow permission cannot revoke an existing grant. Group ancestry/subgroup behavior must be verified for your chosen users; use an isolated top-level group without subgroups for initial acceptance.

Do not call `/users-management-permissions` or per-group `/management/permissions` for this flow. Those routes are FGAP V1 mechanisms and are not the delegation permission payload used here.

## Existing ILM provider

Delegation requires the matching deployed ILM delegation support, including `allowedDelegates` and `delegateClaim`. Old deployed ILM versions without these fields cannot support this target. The update script stops if those fields are absent.

For delegation mode, supply a short-lived existing ILM administrator token privately and run:

```bash
IFS= read -r -s MCP_ILM_ADMIN_TOKEN
printf '%s' "$MCP_ILM_ADMIN_TOKEN" > "$MCP_SETUP_DIR/ilm-admin.token"
unset MCP_ILM_ADMIN_TOKEN
export ILM_ADMIN_TOKEN_FILE="$MCP_SETUP_DIR/ilm-admin.token"
./scripts/keycloak/update-ilm-provider.sh "$MCP_SETUP_DIR/config.json"
```

This reads and updates only `GET/PUT {ilmApiBaseUrl}/v1/settings/authentication/oauth2Providers/{existing-provider-name}`. It verifies the issuer and `clientId=ilm`, preserves all existing update fields, removes response-only `name`, `jwkSetKeys`, `jwkSetLoadFailure`, **omits `clientSecret`**, and adds only `ilm-mcp` to the existing `allowedDelegates`. It requires existing `delegateClaim=client_id`. The required ILM `persistOAuth2Provider` implementation preserves the stored secret when `clientSecret` is absent/null/empty. Confirm that implementation is deployed before this PUT; do not send a masked or encrypted GET secret back.

The expected effective change is only:

```json
{"allowedDelegates":["<existing entries preserved>","ilm-mcp"]}
```

That JSON illustrates the merge result; **do not send it as a partial provider PUT**. Provider PUT persists the full DTO, so the supplied script performs GET/merge/PUT to preserve issuer, `clientId=ilm`, audiences, claim selection, URLs, scopes, skew and session settings. Do not change existing provider audiences to the MCP public resource. ILM should continue accepting B for its existing API audience.

## Obtain A, then probe exchange before deployment

Use the selected source OAuth client's existing authorization-code + PKCE flow, with exact registered redirect URI, random state and S256 challenge. In delegation mode request:

```text
GET {issuer}/protocol/openid-connect/auth
  ?client_id={source-client-id}
  &response_type=code
  &redirect_uri={exact-registered-redirect-uri}
  &scope=openid%20mcp%3Aaccess%20delegation%3Aclient%3Ailm-mcp
  &state={random-state}
  &code_challenge={S256-challenge}
  &code_challenge_method=S256
```

The actual MCP client may also send its standard public `resource` parameter. This runbook does not assume Keycloak implements resource indicators: audience isolation comes from the configured mappers and token checks. Never substitute a password grant for interactive user consent. Store the source access token or complete code-exchange response privately in `$MCP_SETUP_DIR/source.json`; tokens must not be printed or pasted into chat. Users outside `ilm-mcp-users` may still complete login, but the unauthorized delegation scope is dropped and A lacks `may_act`. Treat that as denial, not a reason to retry ordinary exchange.

Retrieve/provision `ilm-mcp`'s current client secret into a private secret file through your existing secret manager. If using Admin REST, the endpoint is `GET /admin/realms/{realm}/clients/{ilm-mcp-internal-uuid}/client-secret`; write response field `value` directly to a 0600 file with a script that reads the admin token privately. Do not use `POST .../client-secret`, which rotates it, and do not print the response. For a direct operator API retrieval without writing your own request script, run:

```bash
./scripts/keycloak/read-mcp-secret.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/mcp.secret"
```

This helper performs only GETs, refuses redirects/overwrites and writes the current secret directly to a private 0600 file.

```bash
# Files source.json and mcp.secret must already have mode 0600.
mkdir "$MCP_SETUP_DIR/probe"
chmod 700 "$MCP_SETUP_DIR/probe"
export SOURCE_TOKEN_FILE="$MCP_SETUP_DIR/source.json"
export MCP_SECRET_FILE="$MCP_SETUP_DIR/mcp.secret"
export PROBE_OUTPUT_DIR="$MCP_SETUP_DIR/probe"
./scripts/keycloak/exchange-token-probe.sh "$MCP_SETUP_DIR/config.json"
```

The probe reads secrets/tokens only from private files, constructs request bodies in memory, suppresses HTTP response/error bodies, follows no redirects, and stores the actor and exchange token responses in exclusive 0600 files. It prints only checks and file-handling status. Use a fresh output directory for each run.

Delegation requests are:

```text
POST {issuer}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
client_id=ilm-mcp
client_secret=<private value supplied in memory>
```

```text
POST {issuer}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:token-exchange
client_id=ilm-mcp
client_secret=<private value supplied in memory>
subject_token=<A supplied in memory>
subject_token_type=urn:ietf:params:oauth:token-type:access_token
actor_token=<service-account token supplied in memory>
actor_token_type=urn:ietf:params:oauth:token-type:access_token
requested_token_type=urn:ietf:params:oauth:token-type:access_token
audience=ilm
scope=openid ilm-mcp-upstream
```

The scope and mappers of the requesting client `ilm-mcp`, not the target client `ilm`, build B. `audience=ilm` alone cannot recreate username or roles. Compare B's relevant claims privately to a successful existing ILM user token, then call one authorized read-only ILM endpoint with B for two differently privileged users. Confirm their distinct expected permissions; no new user role grants are part of this guide. The probe's JWT decoding is only a shape check; it does not replace signature validation, live ILM authentication or role authorization tests.

Before enabling shared MCP, verify negative cases: A with only the public resource audience; A with only requester ID; A with `aud=ilm`; wrong issuer/expired A; missing scope; unselected user; missing/other `may_act`; wrong actor token; B without the expected username/roles; denied ILM read by the lower-privilege user. A standard exchange of an A carrying `may_act` must fail. Never log raw A, actor token or B while testing these cases.

## Explicit ordinary Standard V2 alternative

Choose this separately by setting `mode=standard`, applying that config, and deploying MCP with `exchange-mode=standard`. No `token-exchange-delegation` or `parameterized-scopes` feature flags, FGAP delegation permissions, service account, actor token or provider `allowedDelegates` update are required. Existing unrelated feature flags/permissions and any existing service-account identity are left in place. Only a new standard requester has its service account disabled by default. The requester remains confidential with Standard Token Exchange ON and Full Scope Allowed OFF.

Request a **fresh A** with `scope=openid mcp:access`, omitting `delegation:client:ilm-mcp`. The ordinary exchange uses the same form as above **without** `actor_token` and `actor_token_type`. The provided probe chooses that form explicitly from config. A and B must have no `may_act`/`act`. Keep the same A audience isolation, requester entitlement, B username/roles, issuer and ILM audience checks.

Ordinary V2 does not provide RFC 8693 actor attribution or this FGAP group delegation gate. It authenticates the user on B with `azp=ilm-mcp`; it does **not** assert `act.client_id`. Use it only when the deployment is intentionally configured to allow this mode. MCP must never automatically fall back to it after a delegation failure, and it must not enforce an `act` claim in this explicitly selected ordinary mode.

## Deploy the MCP server

Deploy the [standalone MCP chart](../../deploy/README.md) with your approved configuration values. Identity-provider setup is independent of chart installation; a chart render or successful pod start does not establish Keycloak/token acceptance.

Set `ILM_AUTH_METHOD=oauth-delegation` and the `ilm.mcp.security` properties exposed by the application. For the full actor opt-in, the essential values are:

| Environment setting | Value |
| --- | --- |
| `ILM_MCP_SECURITY_ENABLED` | `true` |
| `ILM_MCP_SECURITY_ISSUER` | Exact existing issuer |
| `ILM_MCP_SECURITY_RESOURCE` | `https://ilm.example.invalid/mcp` |
| `ILM_MCP_SECURITY_JWK_SET_URI` / `ILM_MCP_SECURITY_TOKEN_URI` | Exact issuer-discovery endpoints |
| `ILM_MCP_SECURITY_CLIENT_ID` | `ilm-mcp` |
| `ILM_MCP_SECURITY_CLIENT_SECRET` | Inject from the deployment secret store; never a checked-in value |
| `ILM_MCP_SECURITY_API_AUDIENCE` | `ilm` |
| `ILM_MCP_SECURITY_EXCHANGE_MODE` | Explicit `delegation` |
| `ILM_MCP_SECURITY_EXCHANGE_SCOPE` | `openid ilm-mcp-upstream` |
| `ILM_MCP_SECURITY_AUTHORIZATION_SCOPES` | `openid,mcp:access,delegation:client:ilm-mcp` |
| `ILM_MCP_SECURITY_REQUIRED_SCOPE` | `mcp:access` |
| `ILM_MCP_SECURITY_ALLOWED_ORIGINS` | Approved browser origins only, when required |
| `ILM_MCP_SECURITY_CONNECT_TIMEOUT` / `ILM_MCP_SECURITY_READ_TIMEOUT` | Bounded durations, default `5s` / `15s` |

For ordinary V2, set `ILM_MCP_SECURITY_EXCHANGE_MODE=standard` and authorization scopes `openid,mcp:access`; do not advertise/request delegation scopes. Keep the upstream exchange scope, resource/audience isolation and issuer checks. The code's default mode is ordinary Standard V2; setting `delegation` and supplying a feature-enabled Keycloak image are both required for full actor attribution. There is no automatic fallback.

Use issuer discovery to obtain JWKS and token endpoint URLs; do not assume the sample host is the issuer. Replace the reserved example MCP resource with the exact public resource used in client registration, audience mappings and server configuration. No private credentials belong in checked-in YAML, JSON or shell scripts. Configure and probe the identity-provider exchange before enabling shared MCP access. End-to-end MCP ingress validation, upstream header replacement and log redaction still require deployment acceptance.

Complete [shared HTTP acceptance](../shared-http-acceptance.md) before enabling shared access.

## Version-pinned evidence and limitations

Payload shapes/routes were checked against the **26.8.1 tag**, not inferred from current UI labels:

- Credential lifecycle: [26.8.1 rotation constants](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/models/ClientSecretConstants.java), [reset behavior](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/OIDCClientSecretConfigWrapper.java#L75-L85), [client update hook](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/services/resources/admin/ClientResource.java#L164-L187), [full client representation](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/models/utils/ModelToRepresentation.java), [service-account removal](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/services/managers/ClientManager.java).
- Non-code grant flags: [26.8.1 device attribute and enablement](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi/src/main/java/org/keycloak/models/OAuth2DeviceConfig.java), [CIBA attribute and enablement](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi/src/main/java/org/keycloak/models/CibaConfig.java), [JWT authorization grant attribute](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/protocol/oidc/OIDCConfigAttributes.java).
- [Client representation](https://github.com/keycloak/keycloak/blob/26.8.1/core/src/main/java/org/keycloak/representations/idm/ClientRepresentation.java), [OIDC attributes](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/protocol/oidc/OIDCConfigAttributes.java), [refresh enum](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/OIDCAdvancedConfigWrapper.java), [feature definitions](https://github.com/keycloak/keycloak/blob/26.8.1/common/src/main/java/org/keycloak/common/Profile.java).
- [Standard V2 provider](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/tokenexchange/StandardTokenExchangeProvider.java), [delegation provider](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/tokenexchange/TokenExchangeDelegationProvider.java), [client delegation scope](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/scope/ClientDelegationScopeType.java), [audience mapper](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/protocol/oidc/mappers/AudienceProtocolMapper.java).
- [FGAP schema](https://github.com/keycloak/keycloak/blob/26.8.1/server-spi-private/src/main/java/org/keycloak/authorization/fgap/AdminPermissionsSchema.java), [FGAP permission test helpers](https://github.com/keycloak/keycloak/blob/26.8.1/tests/base/src/test/java/org/keycloak/tests/admin/authz/fgap/PermissionTestUtils.java), [three-client delegation tests](https://github.com/keycloak/keycloak/blob/26.8.1/tests/base/src/test/java/org/keycloak/tests/oauth/tokenexchange/ClientAgentDelegationTest.java), [client delegation tests](https://github.com/keycloak/keycloak/blob/26.8.1/tests/base/src/test/java/org/keycloak/tests/oauth/tokenexchange/ClientDelegationTest.java).
- [Client policy representation](https://github.com/keycloak/keycloak/blob/26.8.1/core/src/main/java/org/keycloak/representations/idm/authorization/ClientPolicyRepresentation.java), [scope representation](https://github.com/keycloak/keycloak/blob/26.8.1/core/src/main/java/org/keycloak/representations/idm/authorization/ScopePermissionRepresentation.java), [policy fields](https://github.com/keycloak/keycloak/blob/26.8.1/core/src/main/java/org/keycloak/representations/idm/authorization/AbstractPolicyRepresentation.java).
- [Client authz route](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/services/resources/admin/ClientResource.java), [resource-server routing](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/authorization/admin/ResourceServerService.java), [policy/search/type routing](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/authorization/admin/PolicyService.java), [typed policy handling](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/authorization/admin/PolicyTypeService.java), [permission routing](https://github.com/keycloak/keycloak/blob/26.8.1/services/src/main/java/org/keycloak/authorization/admin/PermissionService.java).
