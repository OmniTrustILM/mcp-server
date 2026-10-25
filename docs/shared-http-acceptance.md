# Shared HTTP authentication acceptance

Run these checks in an isolated environment with the deployment's configured identity provider before enabling shared MCP access. Start with the [provider-neutral OAuth configuration](shared-http-authentication.md). The [Keycloak operator quickstart](keycloak/README.md) and [API configuration guide](keycloak/api-configuration.md) are a provider-specific example with private-file setup/probe commands. Offline fixtures validate local script behavior only.

Use two authorized users with different existing ILM permissions and, for restricted delegation, one unselected user. Keep tokens, authorization codes, PKCE verifiers and secrets outside the repository/chat/logs. Record pass/fail, test-user aliases and public identifiers only; never store raw token responses in an acceptance report. Coordinate provider edits and credential-rotation jobs.

## Prerequisites

- The exact issuer, trusted signing-key/token endpoints, existing ILM OAuth provider and ILM API audience are confirmed.
- The issuer supports the explicitly selected exchange mode. For actor delegation, the deployed ILM version supports delegation settings and permits the configured MCP delegate.
- The confidential MCP requester is permitted to exchange tokens. Public frontend registrations use exact installed-client callbacks, PKCE S256 and authorization-code flow only.
- Any actor-delegation permission and consent requirement is restricted to the intended users and requester. Existing broader grants and group inheritance are audited.
- Existing username/role mapping selections are verified without granting new user roles. A is isolated from `ilm`; B uses the existing ILM API audience and user's permissions.
- The MCP chart in `deploy/charts/mcp-server` uses private secret references, an explicit exchange mode, and login/exchange scopes matching the issuer configuration.

## Discovery, login and token isolation

| Check | Required result |
| --- | --- |
| Unauthenticated protected-resource metadata/challenge | Correct public MCP resource, existing issuer and configured authorization scopes; no secret or ILM token. |
| Authorized user approves source OAuth login | Signed A has the exact public MCP resource audience, excludes the ILM API audience, and includes the required scope and the issuer's permissions for the selected mode. |
| Unselected user logs in, in restricted delegation mode | Delegated MCP access fails closed. Login success alone is insufficient. |
| User declines consent | No delegated MCP access. |
| Valid exchange | Signed B is valid for the same issuer/user `sub`, has the ILM API audience and expected username/roles, and excludes the public MCP resource. Delegation mode includes the matching immediate `act.client_id`; standard mode has no actor claim. |
| Wrong issuer/signature/expiry, missing required scope, missing MCP resource audience, or A includes the ILM API audience | MCP rejects before an ILM tool call. |
| Missing/wrong `may_act`, actor token, actor type or actor binding | Delegation fails; ordinary exchange is never attempted as fallback. |
| Ordinary exchange of A carrying `may_act`/`act` | Rejected. |
| Mismatched B subject/audience/issuer/signature/expiry/actor | Rejected; A is never forwarded as an alternative ILM credential. |

Acceptance must verify signatures using the issuer's trusted keys and exercise MCP/ILM authentication paths; unsigned decoding or a token-shape probe is insufficient.

## User separation and deployment

- Call the same allowed read-only MCP tool as both authorized users, interleaving repeated calls. Each ILM request uses that user's B, never another user's token or a service-account identity.
- Confirm the lower-privilege user is denied an ILM read available to the higher-privilege user. Do not grant additional roles to make the test pass.
- Repeat after failures, cancellation and worker reuse. Stateless HTTP must not require a previous user's MCP session or retain bearer state.
- Confirm upstream Authorization uses B only, inbound token forwarding cannot override it, and authentication failures/outages expose no token/secret/request-body values in logs/responses.
- Verify approved-origin handling, ingress/proxy discovery paths, readiness and timeouts through the deployed public URL.
- For an intentionally selected `standard` deployment, request a fresh A without delegation claims, configure the appropriate login scopes, expect no `act`, and keep the same signed-token/audience/user-separation checks. This is a separate explicit acceptance decision.

## Additional checks for the Keycloak 26.8.1 example

These checks apply when using the supplied [Keycloak integration](keycloak/README.md), rather than defining requirements for every identity provider.

- The existing realm, ILM provider and `clientId=ilm` are confirmed. For delegation, the deployed ILM provider exposes delegation settings and preserves its stored secret when provider PUT omits it.
- For delegation, a feature-enabled optimized Keycloak image preserves other features and enables `token-exchange-delegation,parameterized-scopes`; built-in `delegation:client` is present. Runtime flags/default image assumptions do not satisfy this check.
- Confidential requester `ilm-mcp` has Standard Token Exchange enabled. `/ilm-mcp-users` contains only explicitly selected subjects; consent is required. The FGAP V2 permission targets the concrete group UUID and `delegate-members` with only the `ilm-mcp` client policy.
- Delegation login scopes are `openid,mcp:access,delegation:client:ilm-mcp`. A contains both the public resource and `ilm-mcp` audiences, excludes `ilm`, and carries `may_act.client_id=ilm-mcp` with its service-account UUID. An unselected user's token lacks the delegation permission/`may_act`.
- Delegated B has audience `ilm`, `azp=ilm-mcp`, expected username/roles, and matching `act.client_id`/service-account `act.sub`. It has no public resource audience or `may_act`.
- Ordinary Standard V2 uses a fresh A and authorization scopes `openid,mcp:access`, with no delegation claims or actor parameters. The private exchange probe checks shape and continuity only; finish the signed-token and live authorization checks above.

| Configuration and credential lifecycle check | Required result |
| --- | --- |
| Apply the API configuration twice | Dedicated objects/assignments remain consistent; source clients stay public and retain exact redirects; unrelated settings/user roles remain unchanged. |
| Existing client already matches requested settings | No top-level client PUT is sent. |
| Needed client PUT with current-expiration/rotation metadata | Script aborts before its first mutation. No metadata deletion/replay, rotation or expiration reset is used to bypass it. |
| Delegation → ordinary mode → delegation | Existing service-account UUID and its mappings remain intact. Ordinary execution sends no actor parameters. |
| Existing provider update | Issuer, `clientId=ilm`, username/audience/mapping settings and existing secret remain valid; `ilm-mcp` is added to existing allowed delegates only. |
| Public frontend registration repeated | Matching registration is a no-op; a changed callback or flow fails without PUT. |

Record actual outcomes and any unresolved prerequisite. A successful chart render, offline fixture run, pod startup or private shape probe does not establish end-to-end acceptance.
