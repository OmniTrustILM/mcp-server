# Example Keycloak integration: operator quickstart

This provider-specific example complements the [shared OAuth workflow](../shared-http-authentication.md). These scripts configure an operator-selected existing Keycloak realm and existing ILM provider. `ilm-mcp` is the new confidential MCP exchange actor; `ilm` remains the existing ILM provider/client. Public frontend registrations (`ilm-mcp-codex`, `ilm-mcp-claude-code`) are separate and have no client secret. Python 3, HTTPS access, Keycloak 26.8.1, selected exact callbacks/mappings and private administrator credentials are prerequisites. Start with explicit **delegation** mode. The [detailed runbook](api-configuration.md) contains payloads, prerequisites and version-pinned sources. Required scopes are `openid mcp:access delegation:client:ilm-mcp` for full actor attribution, or `openid mcp:access` for explicitly selected ordinary V2.

1. Fill the public inputs: exact Keycloak base/realm/issuer, existing ILM provider/API prefix, existing public source OAuth client IDs, existing username/role mapping selections, and user UUIDs explicitly selected for `/ilm-mcp-users`. If public source registrations do not exist, create them with the supplied exact-callback templates below. The new group starts empty; listed members are additions only.
2. Inspect existing mappings privately, then mark the two review flags true. A must contain both MCP audiences and exclude `ilm`. B must reproduce the existing ILM username/role claims through the requester's selected mappings.
3. Build/redeploy a feature-enabled optimized Keycloak image with `token-exchange-delegation,parameterized-scopes`, preserving all existing features. Runtime flags alone do not establish support in an already optimized image; do not assume the default image enables them. Deploy matching ILM delegation support before updating the provider. The built-in `delegation:client` scope must exist; the script checks it.
4. Apply the dedicated configuration, update the existing provider additively, obtain a consenting selected user's A, then run the private exchange probe. Validate signed tokens and two users' differing ILM read permissions before enabling the shared MCP deployment.

Use a private terminal without command/session tracing. Scripts can receive credentials in privileged client GET responses; they immediately discard them from returned configuration objects, inspection output and PUTs. Raw responses are not persisted. Transient credential bytes remain subject to Python/process-memory limitations. Only the explicit secret helper saves the current requester secret to a private file.

```bash
# Run these commands from the MCP repository root.
set +x
umask 077
MCP_SETUP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ilm-mcp-setup.XXXXXX")"
chmod 700 "$MCP_SETUP_DIR"
trap 'rm -rf "$MCP_SETUP_DIR"' EXIT HUP INT TERM
cp docs/keycloak/keycloak-input.example.json "$MCP_SETUP_DIR/config.json"
# Edit config.json with the public inputs listed above. Never add credentials.

# Paste a short-lived raw Keycloak admin bearer token privately; read -s hides it.
IFS= read -r -s MCP_KC_ADMIN_TOKEN
printf '%s' "$MCP_KC_ADMIN_TOKEN" > "$MCP_SETUP_DIR/keycloak-admin.token"
unset MCP_KC_ADMIN_TOKEN
export KC_ADMIN_TOKEN_FILE="$MCP_SETUP_DIR/keycloak-admin.token"
# If absent, create only the public frontend registrations you will use:
cp docs/keycloak/public-source-codex.example.json "$MCP_SETUP_DIR/codex-client.json"
cp docs/keycloak/public-source-claude-code.example.json "$MCP_SETUP_DIR/claude-code-client.json"
# Replace callback placeholders with exact callback(s) from the installed client.
# See the detailed guide for Codex callback discovery and Claude Code fixed ports.
./scripts/keycloak/register-public-source-client.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/codex-client.json"
./scripts/keycloak/register-public-source-client.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/claude-code-client.json"
# Add only the frontend IDs you registered to config.json sourceClientIds.
./scripts/keycloak/configure-keycloak.sh inspect "$MCP_SETUP_DIR/config.json" > "$MCP_SETUP_DIR/inspection.json"
# Review inspection.json locally; select exact mappings and set review flags.
# Complete the feature/deployed-ILM prerequisites above, then:
./scripts/keycloak/configure-keycloak.sh apply "$MCP_SETUP_DIR/config.json"
```

Reuse is restricted to enabled public OpenID Connect clients with authorization-code flow enabled, bearer-only disabled, and implicit/password/service-account/device/CIBA/JWT authorization grants disabled. Before any write, apply rejects unsafe flows or missing, wildcard, placeholder, relative, credential-bearing, fragment-bearing or non-HTTPS/non-loopback callbacks; it never silently turns off a flow or replaces a redirect. Inspect shows those flags, grant attributes, exact redirects and the existing PKCE method. The documented setup may explicitly tighten PKCE to S256 only after credential-rotation preflight.

If a client needs updating while credential-rotation metadata is present, apply aborts **before any mutation**. Do not delete rotation metadata or rotate credentials to bypass it. Have the operator reconcile that client change in an isolated, verified 26.8.1 credential-policy workflow. Unchanged client PUTs are skipped. Coordinate against concurrent realm changes/rotation jobs; preflight is not atomic.

Update the **existing** ILM provider and retrieve the current requester secret without printing or rotating it:

```bash
IFS= read -r -s MCP_ILM_ADMIN_TOKEN
printf '%s' "$MCP_ILM_ADMIN_TOKEN" > "$MCP_SETUP_DIR/ilm-admin.token"
unset MCP_ILM_ADMIN_TOKEN
export ILM_ADMIN_TOKEN_FILE="$MCP_SETUP_DIR/ilm-admin.token"
./scripts/keycloak/update-ilm-provider.sh "$MCP_SETUP_DIR/config.json"
./scripts/keycloak/read-mcp-secret.sh "$MCP_SETUP_DIR/config.json" "$MCP_SETUP_DIR/mcp.secret"
```

The new public-client templates use `POST /clients` with public authentication (`none`), PKCE S256, authorization code only and delegation consent. Registration is idempotent; an existing mismatch stops without PUT. Exact callbacks must come from the installed client: no wildcard, guessed desktop callback or unnecessary extra registration. [Detailed frontend registration commands and official sources](api-configuration.md#create-the-public-frontend-registrations-when-absent).

Use the source OAuth client authorization-code/PKCE flow with `scope=openid mcp:access delegation:client:ilm-mcp`. The MCP metadata/challenge must advertise this requested set; check that the frontend actually requests it. The user must be selected in the group and approve consent. Store the code-exchange response or access token privately in `$MCP_SETUP_DIR/source.json` with mode 0600. Never paste it into chat or a command. The [detailed runbook](api-configuration.md#obtain-a-then-probe-exchange-before-deployment) gives the exact form parameters.

```bash
mkdir "$MCP_SETUP_DIR/probe"
chmod 700 "$MCP_SETUP_DIR/probe"
export SOURCE_TOKEN_FILE="$MCP_SETUP_DIR/source.json"
export MCP_SECRET_FILE="$MCP_SETUP_DIR/mcp.secret"
export PROBE_OUTPUT_DIR="$MCP_SETUP_DIR/probe"
./scripts/keycloak/exchange-token-probe.sh "$MCP_SETUP_DIR/config.json"
```

A passing probe checks token shape and identity/audience continuity. It is not signature verification, live ILM authorization acceptance or MCP deployment. Finish the detailed runbook's signed-token, negative-case and two-user read-only authorization checks.

For an intentionally chosen **ordinary Standard V2** deployment, set `mode=standard`, skip delegation feature/provider updates, and obtain a fresh A with `scope=openid mcp:access`, without delegation claims. The same probe sends no actor parameters and expects no `act`. Existing service accounts are retained; a newly created standard requester starts without one. Never switch automatically after delegation failure.

Deploy the MCP chart from this repository at `deploy/charts/mcp-server` with explicit delegation mode and the authorization scope set above. This is independent of Keycloak setup and still requires [shared HTTP acceptance](../shared-http-acceptance.md).

To reproduce offline safety checks locally: `./scripts/keycloak/verify-keycloak-offline.sh`. This intercepts every HTTP call and uses synthetic fixtures; it cannot validate the live server.

## Connect clients

A client needs only the MCP endpoint, for example `https://ilm.example.invalid/mcp`, and finds the issuer and scopes in the protected-resource metadata.

**Claude Code and the hosted Claude apps** sign in with their Client ID Metadata Documents, so no client is registered beforehand: on the first sign-in Keycloak fetches the document from `claude.ai` and creates a public client from it, with consent required. The supplied scripts configure none of what this needs:

- the experimental `cimd` feature, a build option of the optimized image like `token-exchange-delegation` and `parameterized-scopes` above;
- the client policy `mcp-cimd`, whose condition `client-id-uri` matches a client ID that is an `https` URL on `claude.ai` and whose profile applies the `client-id-metadata-document` executor, which trusts `claude.ai`, `localhost` and `127.0.0.1` in these documents;
- `mcp:access` and `delegation:client` as default optional client scopes of the realm, which the created clients get and can request;
- the client policy `mcp-pkce`, which applies the `pkce-enforcer` executor under the condition `client-scopes` on the optional scope `mcp:access`, so that every client requesting it uses PKCE S256, including these.

Claude Code users add the server and sign in from `/mcp`:

```bash
claude mcp add --transport http --scope user ilm https://ilm.example.invalid/mcp
```

In the hosted apps, an organization owner adds a custom connector with the MCP endpoint, and members connect to it. These connectors reach MCP and Keycloak from Anthropic's cloud, so both must be reachable from the internet.

**Codex** cannot sign in with its metadata document yet: Keycloak 26.8.1 rejects it ([keycloak#51236](https://github.com/keycloak/keycloak/issues/51236)). Register a public client by hand, such as the supplied `ilm-mcp-codex` template, with PKCE S256, consent required and the loopback redirect `http://127.0.0.1/callback`, which matches any local port. Name it in Codex's `oauth.client_id` and set `scopes` to what MCP needs: without `scopes`, Codex requests every scope Keycloak advertises, and Keycloak rejects those the client lacks. Add the entry by editing `~/.codex/config.toml`, because `codex mcp add` signs in before `scopes` can be set. In `standard` mode, leave out `delegation:client:ilm-mcp`:

```toml
[mcp_servers.ilm]
url = "https://ilm.example.invalid/mcp"
scopes = ["openid", "mcp:access", "delegation:client:ilm-mcp"]

[mcp_servers.ilm.oauth]
client_id = "ilm-mcp-codex"
callback_url = "http://127.0.0.1/callback"
```

Run `codex mcp logout ilm` after changing `scopes`, then sign in with `codex mcp login ilm`. Without `oauth.client_id`, Codex uses its metadata document and sign-in fails.
