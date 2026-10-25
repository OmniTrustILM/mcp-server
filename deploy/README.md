# Deploy the ILM MCP server

The standalone Helm chart lives at [`charts/mcp-server`](charts/mcp-server). Install
it directly or include it as an umbrella dependency. See the
[chart configuration and identity-provider requirements](charts/mcp-server/README.md)
for the complete values reference.

## Install

Prepare `mcp-values.yaml` and install a reviewed chart from the official OCI
repository. Change the example release name `ilm-mcp` and namespace `ilm` as needed:

```sh
helm upgrade --install ilm-mcp \
  oci://hub.omnitrustregistry.com/ilm-helm/mcp-server \
  --version '<reviewed-chart-version>' \
  --namespace ilm --create-namespace \
  --values mcp-values.yaml
```

The values must supply the public HTTPS MCP resource, ILM API and trusted OAuth
endpoints, an existing confidential-client Secret reference, and explicit network
peers for ingress and egress. The chart creates the workload; it creates no
credentials, identity-provider registrations, persistent volumes or public Ingress.
Keep credentials in the deployment's secret manager. Set `image.pullSecrets` when
application-image pulls require registry credentials; Helm registry authentication
is separate from Kubernetes image-pull Secrets.

For a local source checkout, build the chart's locked dependencies and use the
local chart path instead of the OCI reference:

```sh
helm dependency build deploy/charts/mcp-server
helm upgrade --install ilm-mcp \
  deploy/charts/mcp-server \
  --namespace ilm --create-namespace \
  --values mcp-values.yaml
```

## Choose chart and image versions

Use a reviewed immutable chart version for repeatable deployments. Development
versions have the form `0.1.0-develop.<full-40-character-commit-SHA>`; release charts
use stable versions such as `0.1.0`. The `0.0.0` registry alias points to the latest
successful development chart publication and can move. It is available when you
intentionally want to follow development builds.

Chart versions and application image references are independent. `image.tag`
defaults to the chart's `appVersion`; a configured `image.digest` takes precedence.
Pin a reviewed image digest and verify the shared HTTP security configuration before
allowing traffic. Verify chart signatures with the organization's distributed
public key before deployment.

## Configure shared authentication

Follow the [provider-neutral OAuth setup](../docs/shared-http-authentication.md)
and complete the [acceptance checks](../docs/shared-http-acceptance.md) with your
identity provider and ILM instance. The [Keycloak integration](../docs/keycloak/README.md)
is one provider-specific example. Installing the chart or starting its pods does
not establish user login, token exchange, ILM authorization or delegated audit
acceptance. Rotate confidential-client credentials through your secret manager and
roll the pods after updating the referenced Secret.

## Umbrella dependency

An umbrella chart can consume the same published artifact without moving its
source. For example:

```yaml
dependencies:
  - name: mcp-server
    alias: mcpServer
    version: '<reviewed-chart-version>'
    repository: oci://hub.omnitrustregistry.com/ilm-helm
    condition: mcpServer.enabled
```

Pass MCP values under `mcpServer` and shared image registry/pull-secret settings
under `global`. The parent's dependency condition controls inclusion; the MCP
chart has no activation switch. Its API, OAuth, network and existing Secret
references remain explicit. The standalone chart does not require an ILM umbrella,
database, messaging service or session store.
