# ILM MCP server

Standalone deployment of the stateless HTTP MCP server. Authentication uses an external OAuth issuer and
exchanges each caller's MCP access token for a separate ILM access token. This chart does not configure an identity
provider, create ILM users, issue tokens or store user sessions.

Installing this chart always renders its resources. Supply the required public OAuth configuration, existing
credential Secret reference and network peers; unconfigured defaults fail validation. There is no internal
activation switch. A parent chart can use its own dependency condition, for example an alias
`mcpServer` with `condition: mcpServer.enabled`. That parent value controls Helm's dependency selection and is
accepted as metadata here; it does not suppress a standalone installation. As a dependency of the ILM chart, this
chart derives most of its settings from the ILM chart's globals; see [Umbrella mode](#umbrella-mode).

## Configuration

| Value | Purpose |
| --- | --- |
| `image.registry` / `repository` / `name` | Application image location |
| `image.tag` | Image tag; empty falls back to `Chart.AppVersion` |
| `image.digest` | Optional `sha256` digest, taking precedence over the tag; pin the reviewed image for shared deployments |
| `global.image.registry` / `repository` / `pullSecrets` | Umbrella image overrides and pull secrets, merged by `ilm-lib` |
| `apiUrl` | Required ILM API base including `/api`; HTTPS, or HTTP with `security.allowHttpBackchannel` |
| `access` / `toolsets` | Operator tool ceiling; default `read-only` / `all` |
| `security.issuer` | Required exact external token issuer |
| `security.resource` | Required public MCP resource URL advertised to clients |
| `security.jwkSetUri` / `tokenUri` | Required trusted verification and exchange endpoints; HTTPS, or HTTP with `security.allowHttpBackchannel` |
| `security.allowHttpBackchannel` | Default `false`; `true` allows `http://` for `apiUrl`, `jwkSetUri` and `tokenUri` only, never for the issuer or resource |
| `security.clientId` | Required confidential MCP exchange client |
| `security.clientSecretRef.name` / `key` | Required existing Secret and client-secret key; no inline credential |
| `security.apiAudience` | Downstream ILM audience; default `ilm`, distinct from the MCP resource |
| `security.exchangeMode` | `standard` or `delegation`; no automatic fallback. Empty (default) renders `standard` outside umbrella mode, so `managed` mode can tell a set value from the default |
| `security.exchangeScope` | Fixed downstream exchange scope; empty uses issuer defaults |
| `security.requiredScope` | Required incoming MCP scope; default `mcp:access` |
| `security.authorizationScopes` | Additional login scopes advertised to clients; default empty |
| `security.jwsAlgorithms` | Accepted asymmetric JWT algorithms: RS256/384/512, ES256/384/512, PS256/384/512; default `[RS256]` |
| `security.allowedOrigins` | Allowed browser origins; empty permits clients without Origin |
| `replicaCount` | One or more stateless replicas; no session affinity |
| `networkPolicy.enabled` | Render the NetworkPolicy; default `true` |
| `networkPolicy.ingress` | Explicit NetworkPolicy peers permitted to reach MCP; required while `networkPolicy.enabled` is true |
| `networkPolicy.egress` | Explicit rules for ILM and identity-provider destinations; required while `networkPolicy.enabled` is true |

Use `security.allowHttpBackchannel` only on a network you trust; see
[server settings](../../../docs/shared-http-authentication.md#server-settings).

While `networkPolicy.enabled` is true, both network-policy peer lists must be nonempty. DNS to kube-system pods
labelled `k8s-app: kube-dns` is included. Verify that selector against the target cluster. Destination changes require
updating the configured egress rules; no unrestricted Internet rule is supplied. NetworkPolicy enforcement requires the
cluster's CNI. With `networkPolicy.enabled: false` the chart renders no NetworkPolicy, so only policies defined
elsewhere restrict these pods.

The defaults run as a non-root user with a read-only root filesystem, dropped capabilities and a bounded temporary
directory. Pods and created ServiceAccounts always disable automatic service-account token mounting. The Service
is internal; configure public HTTPS routing separately for MCP and its protected-resource metadata, including
`/.well-known/oauth-protected-resource/mcp`.

## Umbrella mode

The ILM chart installs this chart as its dependency `mcpServer` and sets `global.mcp.enabled`, which puts the chart in
umbrella mode: it reads the ILM chart's globals and derives the settings that ILM's own Services and the bundled
Keycloak determine. Without `global.mcp.enabled` these globals have no effect, and the values above apply as described.

| Value | Purpose |
| --- | --- |
| `global.mcp.enabled` | Umbrella mode; default `false` |
| `global.mcp.identity.mode` | `managed` (default): MCP's users sign in through the bundled Keycloak, which the ILM chart prepares for MCP. `external`: through a provider the platform does not manage |
| `global.hostName` | ILM's public host, required in umbrella mode; empty falls back to the chart's own `hostName`, as in the ILM chart |
| `global.keycloak.enabled` | Whether the ILM chart runs the bundled Keycloak; `managed` requires it |
| `global.mcp.clientSecret` | `managed`: MCP's client secret, which the ILM chart renders into the Secret `mcp-server-secret`, key `client-secret`. The pod carries its hash as `checksum/client-secret`, so it rolls when the secret changes |
| `global.mcp.clientSecretRef.name` / `key` | `managed`: an existing Secret that holds MCP's client secret instead; `key` defaults to `client-secret` |

In umbrella mode, with `<host>` the host above:

| Value | `managed` | `external` |
| --- | --- | --- |
| `apiUrl` | `http://core-service:8080/api` unless set | the same |
| `security.resource` | `https://<host>/mcp`; setting it fails | `https://<host>/mcp` unless set |
| `security.issuer` | `https://<host>/kc/realms/ILM`; setting it fails | required |
| `security.jwkSetUri` | `http://keycloak-internal-service:8080/kc/realms/ILM/protocol/openid-connect/certs` unless set | required |
| `security.tokenUri` | `http://keycloak-internal-service:8080/kc/realms/ILM/protocol/openid-connect/token` unless set | required |
| `security.clientId` | `ilm-mcp`; setting it fails | required |
| `security.clientSecretRef` | `global.mcp.clientSecretRef`, else `{name: mcp-server-secret, key: client-secret}`; setting `name` fails | `name` required, `key` defaults to `client-secret` |
| `security.exchangeMode` | `delegation`; setting it fails | required |
| `security.exchangeScope` | `openid ilm-mcp-upstream`; setting it fails | as set |
| `security.authorizationScopes` | `[openid, delegation:client:ilm-mcp]`; setting them fails | as set |
| `security.allowHttpBackchannel` | `true` | `true`, because the ILM API is in-cluster `http` |

The other values, such as `security.requiredScope` and `security.apiAudience`, apply as in a standalone install.

In `managed` mode the ILM chart's realm import defines MCP's client and scopes. It cannot see a value set here, which
would then not match, so setting one fails the render. `apiUrl`, `jwkSetUri` and `tokenUri` can be set, for a Core or
Keycloak Service on another port.

In `external` mode the values carry the provider's settings. `security.issuer` is `https` without userinfo, query or
fragment. `security.jwkSetUri` and `security.tokenUri` are `https`, or `http` only to a Kubernetes Service host,
`<service>.<namespace>.svc` optionally followed by the cluster domain. While `global.keycloak.enabled` is true, an issuer
under `https://<host>/kc/`, compared case-insensitively and with or without `:443`, is the bundled Keycloak and fails
the render; use `managed` for it.

While a proxy is set, umbrella mode appends the hosts of MCP's `http://` URLs, such as `core-service` and
`keycloak-internal-service`, to `NO_PROXY`, except for those it lists already. A proxy supplied through
`additionalEnv.secrets` instead of the proxy values is invisible to the chart, so its `NO_PROXY` must list MCP's
in-cluster hosts itself.

## Deployment customization

The chart follows the ILM application-chart conventions and pins the published `ilm-lib` library at `1.4.2-1`.

| Value | Purpose |
| --- | --- |
| `nameOverride` / `fullnameOverride` | Resource and selector naming; chart-derived names are lowercased for aliases such as `mcpServer` |
| `service.name` | Name of the Service; empty uses the chart's full name |
| `podLabels` / `podAnnotations` | Additional pod metadata; selector labels cannot be replaced |
| `nodeSelector` / `tolerations` / `affinity` | Pod scheduling |
| `serviceAccount.create` / `name` / `annotations` | Create an account (default), or reference an existing account; empty name with `create: false` uses `default` |
| `terminationGracePeriodSeconds` | Pod termination grace period; default 30 seconds |
| `podSecurityContext` / `image.securityContext` | Pod and main-container security settings, with hardened defaults |
| `image.resources` | Main-container CPU and memory requests and limits |
| `image.command` / `image.args` | Optional templated command and argument arrays |
| `image.probes.startup` / `liveness` / `readiness` | Probe `enabled`, `custom` handler, and timing/threshold settings |
| `volumes.ephemeral.sizeLimit` / `medium` | Bounded `/tmp` emptyDir; default `128Mi`, optional `Memory` medium |
| `initContainers` | Additional templated init containers |
| `additionalVolumes` / `additionalVolumeMounts` | Additional templated volumes and main-container mounts |
| `additionalEnv.variables` | Additional templated Kubernetes EnvVar entries, including `valueFrom` references |
| `additionalEnv.configMaps` / `additionalEnv.secrets` | Existing ConfigMap and Secret names imported using `envFrom` |

`global.initContainers`, `global.additionalVolumes`, `global.additionalVolumeMounts` and `global.additionalEnv`
have the same shapes; global entries precede local entries. These are administrator-controlled customization hooks.
Additional variable names are checked after template rendering and cannot duplicate chart-owned authentication,
transport or application environment variables. Kubernetes gives explicit `env` entries precedence over `envFrom`
imports, so imported maps and Secrets cannot replace the chart's mandatory environment values. Other umbrella
metadata and unrelated `global` values are accepted; known application configuration remains validated.
Command/args and Java/Spring property overrides remain available to the deployment administrator.

Liveness and readiness use the local health endpoints and do not sign in as a user.

## Trusted certificates and proxy

MCP reads the trust and proxy values that the other ILM charts read, under the same names.

| Value | Purpose |
| --- | --- |
| `global.trusted.certificates` | PEM certificates trusted in addition to the JVM's own. While set, MCP reads `TRUSTED_CERTIFICATES` from the Secret `trusted-certificates`, key `ca.crt`, which the umbrella renders from the same value. This chart renders no Secret for it |
| `trusted.certificates` | The same for an install without the umbrella: the chart renders the Secret `trusted-certificates-<chart name>`, `trusted-certificates-mcp-server` standalone, from it and reads that. The global value takes precedence |
| `global.httpProxy` / `global.httpsProxy` | Proxy for `http` and `https` requests, `http://[user[:password]@]host:port`; empty means none |
| `httpProxy` / `httpsProxy` | The same for an install without the umbrella; the global value takes precedence |
| `global.noProxy` / `noProxy` | Hosts that bypass the proxy |

An empty value counts as unset. `TRUSTED_CERTIFICATES`, `HTTP_PROXY`, `HTTPS_PROXY` and `NO_PROXY` are rendered only
when their value is set, and `additionalEnv.variables` cannot set them. `TRUSTED_CERTIFICATES` takes the value ILM Core
takes: MCP reads its certificate blocks and ignores anything else. The pod carries the annotations
`checksum/trusted-certificates-global` and `checksum/trusted-certificates-local` for the values that are set, so it
rolls when the certificates change.

A proxy URL must be one that MCP accepts at startup, as described under
[outbound connections](../../../docs/shared-http-authentication.md#outbound-connections). Any other URL fails the
render with `httpProxy must be an http://host:port URL for the MCP server` (`httpsProxy` for that value), without
printing the URL. The proxy values are rendered into the Deployment as given, so a URL with credentials is visible to
whoever can read the Deployment. To keep one in a Secret instead, leave the proxy values empty and import a Secret that
holds `HTTP_PROXY` or `HTTPS_PROXY` through `additionalEnv.secrets`; the chart does not check such a URL.

For the `NO_PROXY` format, the in-cluster hosts to list and the clear-text risk of an incomplete list, see
[outbound connections](../../../docs/shared-http-authentication.md#outbound-connections). While `networkPolicy.enabled`
is true, `networkPolicy.egress` must allow the proxy.

## Identity-provider setup

Reuse the ILM OAuth issuer and give MCP its own confidential exchange client. Client applications use their own
supported OAuth registrations and authorization code with PKCE; they never receive the exchange credential.
`standard` uses ordinary token exchange without actor-token delegation. `delegation` additionally requires compatible
issuer policies, an actor token and a signed immediate `act.client_id` identifying MCP. Configure any required login
scopes explicitly through `security.authorizationScopes` and downstream scopes through `security.exchangeScope`.

See the [shared HTTP authentication guide](../../../docs/shared-http-authentication.md) and the optional
[Keycloak setup guide](../../../docs/keycloak/README.md) for application and provider-specific requirements.
Create the referenced Secret using the deployment's secret manager or Sealed Secrets workflow. This chart renders
no Secret for credentials; the only Secret it can render holds the public certificates of `trusted.certificates`.
Changing the client secret requires a pod rollout because it is consumed through an environment reference. Keep token
values, credentials and private exported material out of manifests and logs.

## Deployment

See the [deployment guide](../../README.md) for installation and chart version selection.

Before shared traffic, test real client discovery/login, two differently privileged ILM users, rejected tokens and
exchanges, audit attribution when using delegation, multiple replicas and restart continuity. Operator tool access
does not grant ILM permissions. Sensitive-output tools require `full` and the caller's existing ILM authorization.
