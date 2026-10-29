# ILM MCP server

Standalone deployment of the stateless HTTP MCP server. Authentication uses an external OAuth issuer and
exchanges each caller's MCP access token for a separate ILM access token. This chart does not configure an identity
provider, create ILM users, issue tokens or store user sessions.

Installing this chart always renders its resources. Supply the required public OAuth configuration, existing
credential Secret reference and network peers; unconfigured defaults fail validation. There is no internal
activation switch. A parent chart can use its own dependency condition, for example an alias
`mcpServer` with `condition: mcpServer.enabled`. That parent value controls Helm's dependency selection and is
accepted as metadata here; it does not suppress a standalone installation.

## Configuration

| Value | Purpose |
| --- | --- |
| `image.registry` / `repository` / `name` | Application image location |
| `image.tag` | Image tag; empty falls back to `Chart.AppVersion` |
| `image.digest` | Optional `sha256` digest, taking precedence over the tag; pin the reviewed image for shared deployments |
| `global.image.registry` / `repository` / `pullSecrets` | Umbrella image overrides and pull secrets, merged by `ilm-lib` |
| `apiUrl` | Required HTTPS ILM API base including `/api` |
| `access` / `toolsets` | Operator tool ceiling; default `read-only` / `all` |
| `security.issuer` | Required exact external token issuer |
| `security.resource` | Required public MCP resource URL advertised to clients |
| `security.jwkSetUri` / `tokenUri` | Required trusted HTTPS verification and exchange endpoints |
| `security.clientId` | Required confidential MCP exchange client |
| `security.clientSecretRef.name` / `key` | Required existing Secret and client-secret key; no inline credential |
| `security.apiAudience` | Downstream ILM audience; default `ilm`, distinct from the MCP resource |
| `security.exchangeMode` | Explicit `standard` (default) or `delegation`; no automatic fallback |
| `security.exchangeScope` | Fixed downstream exchange scope; empty uses issuer defaults |
| `security.requiredScope` | Required incoming MCP scope; default `mcp:access` |
| `security.authorizationScopes` | Additional login scopes advertised to clients; default empty |
| `security.jwsAlgorithms` | Accepted asymmetric JWT algorithms: RS256/384/512, ES256/384/512, PS256/384/512; default `[RS256]` |
| `security.allowedOrigins` | Allowed browser origins; empty permits clients without Origin |
| `replicaCount` | One or more stateless replicas; no session affinity |
| `networkPolicy.ingress` | Required explicit NetworkPolicy peers permitted to reach MCP |
| `networkPolicy.egress` | Required explicit rules for ILM and identity-provider destinations |

Both network-policy peer lists must be nonempty. DNS to kube-system pods labelled `k8s-app: kube-dns` is included.
Verify that selector against the target cluster. Destination changes require updating the configured egress rules;
no unrestricted Internet rule is supplied. NetworkPolicy enforcement requires the cluster's CNI.

The defaults run as a non-root user with a read-only root filesystem, dropped capabilities and a bounded temporary
directory. Pods and created ServiceAccounts always disable automatic service-account token mounting. The Service
is internal; configure public HTTPS routing separately for MCP and its protected-resource metadata, including
`/.well-known/oauth-protected-resource/mcp`.

## Deployment customization

The chart follows the ILM application-chart conventions and pins the published `ilm-lib` library at `1.4.2-1`.

| Value | Purpose |
| --- | --- |
| `nameOverride` / `fullnameOverride` | Resource and selector naming; chart-derived names are lowercased for aliases such as `mcpServer` |
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

Pass `TRUSTED_CERTIFICATES`, `HTTP_PROXY`, `HTTPS_PROXY` and `NO_PROXY` through `additionalEnv`, and keep a proxy URL
that carries credentials in a Secret behind `valueFrom`. `TRUSTED_CERTIFICATES` takes the value ILM Core takes: MCP
reads its certificate blocks and ignores anything else. `NO_PROXY` must list every in-cluster host MCP calls, such as
Core and an in-cluster identity provider, or the proxy receives those calls too. `networkPolicy.egress` must allow the
proxy. See the [outbound connections guide](../../../docs/shared-http-authentication.md#outbound-connections) for the
clear-text risk of an incomplete `NO_PROXY`.

Liveness and readiness use the local health endpoints and do not sign in as a user.

## Identity-provider setup

Reuse the ILM OAuth issuer and give MCP its own confidential exchange client. Client applications use their own
supported OAuth registrations and authorization code with PKCE; they never receive the exchange credential.
`standard` uses ordinary token exchange without actor-token delegation. `delegation` additionally requires compatible
issuer policies, an actor token and a signed immediate `act.client_id` identifying MCP. Configure any required login
scopes explicitly through `security.authorizationScopes` and downstream scopes through `security.exchangeScope`.

See the [shared HTTP authentication guide](../../../docs/shared-http-authentication.md) and the optional
[Keycloak setup guide](../../../docs/keycloak/README.md) for application and provider-specific requirements.
Create the referenced Secret using the deployment's secret manager or Sealed Secrets workflow. This chart renders
no Secret objects. Changing the client secret requires a pod rollout because it is consumed through an environment
reference. Keep token values, credentials and private exported material out of manifests and logs.

## Deployment

See the [deployment guide](../../README.md) for installation and chart version selection.

Before shared traffic, test real client discovery/login, two differently privileged ILM users, rejected tokens and
exchanges, audit attribution when using delegation, multiple replicas and restart continuity. Operator tool access
does not grant ILM permissions. Sensitive-output tools require `full` and the caller's existing ILM authorization.
