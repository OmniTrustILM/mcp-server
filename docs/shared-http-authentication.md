# Shared HTTP authentication

Run a shared MCP server as a stateless OAuth protected resource. Clients sign in with the configured external
identity provider using authorization code and PKCE. The MCP server validates the incoming token, then uses its
own confidential client to exchange it for a separate ILM access token for the same user. ILM enforces that user's
existing roles, object permissions and access policies.

```mermaid
sequenceDiagram
    participant C as MCP client
    participant I as OAuth provider
    participant M as MCP server
    participant L as ILM API
    C->>M: Discover protected resource
    M-->>C: Issuer, resource and login scopes
    C->>I: Authorization code with PKCE and user consent
    I-->>C: A: access token for MCP
    C->>M: MCP request with A
    M->>M: Validate signature, issuer, expiry, resource and scope
    M->>I: Exchange A for ILM audience (actor token in delegation mode)
    I-->>M: B: access token for the same user
    M->>M: Validate B and selected exchange mode
    M->>L: API operation with B
    L->>L: Authenticate user and enforce permissions
    L-->>C: Result through MCP
```

The confidential client credential stays on the server. Public client applications use their own OAuth
registrations and never receive it. User tokens exist only during the active request; MCP stores no user session,
grant, refresh token or token cache. Any replica can handle the next request. The issuer may retain its normal
OAuth login sessions, and clients manage their own login/refresh lifecycle.

## Exchange modes

| Mode | Behavior | Prerequisites |
| --- | --- | --- |
| `standard` | Exchange A for B preserving the user; rejects actor/delegation claims | Issuer supports ordinary token exchange, such as Keycloak Standard Token Exchange V2 |
| `delegation` | Exchange A plus a service-account actor token; B must contain the immediate `act.client_id` matching the configured MCP client | Issuer supports explicit actor delegation and consent; ILM 2.21+ permits the verified delegate |

The mode is an operator setting. A denied exchange, missing actor or unavailable issuer fails the request; there
is no fallback to another mode, direct token forwarding or the MCP service account as the ILM user. Standard mode
preserves the user's authorization but does not provide ILM's delegate allowlist or delegate audit attribution.

Both tokens must be signed by the configured issuer and contain a numeric expiration and nonblank subject. A must
target the exact public MCP resource, have the required scope and exclude the ILM audience. B must target ILM,
exclude the MCP resource and identify the same subject. Returning A unchanged from the token endpoint fails.
Delegation mode additionally validates the immediate actor; standard mode rejects `may_act`/`act` on A and `act`
on B. The server accepts JWT access tokens signed with the configured asymmetric algorithms, not opaque tokens.

## Identity-provider configuration

The application configures a trusted OAuth issuer and endpoints explicitly; provider-specific registration and
permission APIs are outside the shared server. Use the provider's documented token-exchange capabilities and
select the exchange mode explicitly.

1. Confirm the existing ILM OAuth provider and exact issuer. A reserved issuer example is
   `https://identity.example.invalid/tenant`; obtain its signing-key and token endpoints from trusted issuer
   metadata. Reuse the existing ILM provider for that issuer and retain its ILM login client and API audiences.
   A second ILM provider with the same issuer is ambiguous.
2. Register the MCP server as a confidential exchange requester and each user-facing client separately as a
   public authorization-code client with PKCE S256 and its exact installed-client callback. Keep the server
   credential private. Configure the source token for the public MCP resource, for example
   `https://ilm.example.invalid/mcp`, without the ILM API audience.
3. Configure the requester's downstream scopes, audiences and username/role mappings so the exchanged token
   represents the same user for ILM. Restrict any actor-delegation permission to the intended users and requester;
   confirm the deployed ILM version supports and permits that delegate before selecting `delegation`.
4. Validate signed source and exchanged tokens, rejected exchanges and different users' existing ILM permissions
   using the [acceptance checklist](shared-http-acceptance.md) before enabling shared access.

The [Keycloak 26.8.1 integration](keycloak/README.md) is one provider-specific example. Its ordinary Standard V2
flow requires no preview server feature. Its actor-delegation flow additionally requires opt-in server features,
a service account, restricted delegation permission and consent; follow that example's version-pinned prerequisites.

## Server settings

Start the HTTP profile with `SPRING_AI_MCP_SERVER_TYPE=SYNC`,
`SPRING_AI_MCP_SERVER_PROTOCOL=STATELESS`, `ILM_AUTH_METHOD=oauth-delegation` and
`ILM_MCP_SECURITY_ENABLED=true`. Startup rejects incomplete or incompatible shared configurations. The default
HTTP profile remains `STREAMABLE`, so set `STATELESS` explicitly.

| Environment variable | Value or default |
| --- | --- |
| `ILM_API_URL` | Required HTTPS ILM API base, including `/api`; HTTP only with `ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL` |
| `ILM_MCP_SECURITY_ISSUER` | Exact trusted issuer string |
| `ILM_MCP_SECURITY_RESOURCE` | Public HTTPS MCP resource, for example `https://ilm.example.invalid/mcp` |
| `ILM_MCP_SECURITY_JWK_SET_URI` | Trusted HTTPS signing-key endpoint; HTTP only with `ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL` |
| `ILM_MCP_SECURITY_TOKEN_URI` | Trusted HTTPS exchange endpoint; HTTP only with `ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL` |
| `ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL` | `true` allows HTTP for `ILM_API_URL`, the signing-key and the exchange endpoint; default `false` |
| `ILM_MCP_SECURITY_CLIENT_ID` | Confidential MCP requester client ID |
| `ILM_MCP_SECURITY_CLIENT_SECRET` | Confidential credential supplied through a Secret reference or private configuration |
| `ILM_MCP_SECURITY_API_AUDIENCE` | `ilm` |
| `ILM_MCP_SECURITY_EXCHANGE_MODE` | `standard` or `delegation`; default `standard` |
| `ILM_MCP_SECURITY_EXCHANGE_SCOPE` | Fixed downstream scope; default empty uses issuer defaults |
| `ILM_MCP_SECURITY_REQUIRED_SCOPE` | `mcp:access` |
| `ILM_MCP_SECURITY_AUTHORIZATION_SCOPES` | Additional comma-separated login scopes; default empty |
| `ILM_MCP_SECURITY_ALLOWED_ORIGINS` | Comma-separated HTTPS browser origins; default empty |
| `ILM_MCP_SECURITY_JWS_ALGORITHMS` | Comma-separated asymmetric allowlist; default `RS256` |
| `ILM_MCP_SECURITY_CONNECT_TIMEOUT` | `5s`; between `100ms` and `60s` |
| `ILM_MCP_SECURITY_READ_TIMEOUT` | `15s`; between `100ms` and `60s` |
| `ILM_MCP_SECURITY_CLOCK_SKEW` | `30s`; between zero and `60s` |
| `TRUSTED_CERTIFICATES` | PEM certificates trusted in addition to the JVM's own; default empty |
| `HTTP_PROXY` | `http://[user[:password]@]host:port` proxy for `http` requests; default none |
| `HTTPS_PROXY` | The same form, for `https` requests; default none |
| `NO_PROXY` | Hosts that bypass the proxy; default empty. List every in-cluster host that MCP calls |
| `ILM_MCP_ACCESS` / `ILM_MCP_TOOLSETS` | `read-only` / `all` |

Supported algorithms are `RS256`, `RS384`, `RS512`, `ES256`, `ES384`, `ES512`, `PS256`, `PS384` and `PS512`.
URLs must be absolute HTTPS without user information or fragments. Only the ILM API, signing-key and exchange URLs
may use HTTP, and only when in-cluster HTTP is allowed as described below. Chart values require query-free URLs.
Configure the issuer/endpoints explicitly;
request headers and token key URLs cannot select a different issuer or destination. All credential-bearing
requests use bounded timeouts and no redirect following, and verify TLS for every HTTPS URL. Trust private
certificate authorities with `TRUSTED_CERTIFICATES`, described under [Outbound connections](#outbound-connections);
shared mode has no TLS verification bypass.

When MCP runs beside ILM, it may call Core and the identity provider's JWKS and token endpoints over in-cluster HTTP,
as Core does. Set `ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL=true` to allow `http://` for `ILM_API_URL`,
`ILM_MCP_SECURITY_JWK_SET_URI` and `ILM_MCP_SECURITY_TOKEN_URI`; they still cannot carry user information or fragments.
The issuer, the resource and the allowed origins stay HTTPS. HTTP exposes the client credential and tokens to anyone on
the path, and lets that party substitute the signing keys so that forged tokens validate. Use it only on a network you
trust.

Login scopes and exchange scopes have separate purposes. Protected-resource metadata advertises the required
scope plus `authorization-scopes`; only `required-scope` is the source scope check. For the Keycloak actor flow,
advertise `openid` and `delegation:client:ilm-mcp` in addition to `mcp:access`. The fixed downstream exchange scope
can be `openid ilm-mcp-upstream` to select ILM user/audience mappings. It is never derived from a caller's tool
arguments. MCP requests no refresh token and never stores one returned by an issuer.

The operator access ceiling determines which tools are registered. ILM still decides whether the user may perform
each operation. Sensitive-output tools require `full` as well as the user's permission; ordinary responses omit
protected values. Authenticated errors and upstream parsing failures must not expose tokens, credentials or raw
upstream bodies in client responses or logs. Keep request/body loggers disabled; never collect bearer headers in
ingress access logs or traces.

## Outbound connections

Shared mode sends its requests to ILM, the signing-key endpoint and the exchange endpoint through one HTTP client. The
variables described below configure that client, and the other authentication modes do not read them. They are named as
ILM Core names them, so they carry no `ILM_` prefix. In `application.yml` they bind under `ilm.mcp.outbound` as
`trusted-certificates`, `http-proxy`, `https-proxy` and `no-proxy`.

`TRUSTED_CERTIFICATES` holds PEM certificates that the client trusts in addition to the JVM's default authorities, so a
server behind a private certificate authority is reachable without a custom trust store. MCP reads every certificate
block as ILM Core does and ignores anything else, such as keys, revocation lists and comments, and it skips a block it
cannot read, with a warning. A value without a readable certificate adds nothing, with a warning, and a blank value
changes nothing. The content of the value never stops startup. While a certificate is added, the client presents no
client certificate, even when `javax.net.ssl.keyStore` is set.

`HTTP_PROXY` serves `http` requests and `HTTPS_PROXY` serves `https` requests, with no fallback from one to the other.
Both take `http://[user[:password]@]host:port`; any other scheme or shape stops startup with
`HTTP_PROXY and HTTPS_PROXY must be http://host:port URLs`. Percent-encode characters that a URL does not allow in
credentials, such as `@` as `%40`. The credentials go to that proxy only, through Basic authentication, and the proxy's
host name is resolved on each connection. While a proxy URL carries credentials, the JDK client throws on a 401 that
offers a Basic challenge or none, even when the request bypasses the proxy. An identity provider that rejects the client
secret usually answers that way, so the refusal is logged as "could not be reached or its answer could not be read" and
fails the request with status 503.

`NO_PROXY` is read as ILM Core reads it, as Java's `http.nonProxyHosts`. Entries are separated by `,` or `|`. Each entry
matches a whole host name without regard to case, and a `*` at its start or end is a wildcard, as in `*.svc`. A leading
dot is not special, so `.svc` matches nothing. Ports and CIDR ranges are not supported. The entries apply to `http` and
`https` requests alike, and loopback hosts (`localhost`, `127.*`, `[::1]`, `0.0.0.0` and `[::0]`) never use the proxy.
List every in-cluster host that MCP calls, such as Core and an in-cluster identity provider, for example
`core,*.svc,*.cluster.local`. Otherwise the proxy receives those calls too. For the in-cluster `http://` endpoints
(Core's API and an in-cluster identity provider), an incomplete `NO_PROXY` sends MCP's client secret and tokens to the
proxy in clear text.

## Diagnosing failures

MCP logs one WARN when the key set cannot be retrieved or read, and when the token endpoint refuses a request, cannot be
reached, or returns an answer or an exchanged token that MCP rejects. A line names what failed and, where there are
any, the chain of exception classes, outermost first, and the HTTP status. It never holds a secret, a token or a
response body. A failure that the sender of a token controls, such as a malformed, expired or wrongly signed token, is
not logged. These lines are how to diagnose trust and proxy problems:

```text
The token endpoint answered HTTP 401 to the token exchange
The key set could not be retrieved or read (JwtException < RemoteKeySourceException < ResourceAccessException < SSLHandshakeException < ValidatorException < SunCertPathBuilderException)
The key set could not be retrieved or read (JwtException < RemoteKeySourceException < HttpClientErrorException, HTTP 407)
```

The second line is what a private certificate authority missing from `TRUSTED_CERTIFICATES` looks like, and the third is
a proxy that refuses MCP. A proxy answers like any server, so when `NO_PROXY` omits an in-cluster host, the proxy's own
errors appear as if the identity provider or ILM had answered. While the key-set endpoint fails, MCP logs one WARN for
each retrieval attempt, and a token that names a key MCP does not hold triggers an attempt.

## Routing and discovery

Publish HTTPS routes for `/mcp` and its subpaths and for
`/.well-known/oauth-protected-resource/mcp`. The root metadata alias
`/.well-known/oauth-protected-resource` returns the same configured resource. With an external prefix or servlet
context path, also map the advertised origin-root metadata route to the application's metadata endpoint.
Missing/invalid tokens receive a 401 bearer challenge pointing at metadata; insufficient scope receives 403.
An unavailable issuer/exchange cannot become an anonymous request. Every MCP request authenticates, including
initialization and tool discovery.

The local public health endpoints are `/actuator/health`, `/actuator/health/liveness` and
`/actuator/health/readiness`; do not publish actuator through the edge. Health checks do not log in or test a
user's permissions. Metadata contains the configured issuer/resource and scopes, never credentials.

Clients without an `Origin` header can connect normally. A supplied browser Origin must match the explicit
allowlist. Allowed preflights permit GET/POST and the supported MCP headers. Do not use wildcard origins or trust
arbitrary forwarded headers to infer resource URLs. Stateless mode requires no session affinity or
`Mcp-Session-Id`; session resumption and durable tasks are not advertised.

## Deployment and acceptance

The [standalone chart](../deploy/README.md) lives with the server under `deploy/charts/mcp-server`.
It supports image tags and digest overrides, requires an existing Secret reference, renders no
credential Secret, and uses explicit ingress/egress policy, non-root containers and no persistent storage. Update
the secret through the deployment's secret manager and roll the pods after credential rotation.

Upgrade every Core replica before saving delegation settings or allowing delegate-bearing audit traffic. Follow
Core's compatible-reader/consistent-restore constraints for rollback. Keep any legacy automatic Keycloak provider
registration disabled when it cannot preserve the delegation fields. Configure the existing persisted realm via
reviewed Admin API changes; a startup realm import is not an update mechanism.

Before enabling shared access, complete the [acceptance checks](shared-http-acceptance.md): real client discovery
and PKCE login, different users' permissions, rejected tokens/exchanges, actor audit behavior, multiple replicas,
restart continuity and redacted failures. Local unit/integration tests or chart installation are not live
identity-provider acceptance. A browserless workload needs its own issuer registration and an ILM-recognized
workload identity; it must pass the same audience/exchange/authorization checks and must not borrow an admin user.

The audience-separated design follows the [MCP security guidance on token passthrough](https://modelcontextprotocol.io/docs/tutorials/security/security_best_practices#token-passthrough)
and [MCP authorization specification](https://modelcontextprotocol.io/specification/latest/basic/authorization).
