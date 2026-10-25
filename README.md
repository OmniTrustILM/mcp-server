# ILM MCP Server

[Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server for the [ILM](https://docs.otilm.com) platform. Exposes certificate and key management operations as AI-friendly tools, enabling LLMs and AI agents to interact with ILM through natural language.

Built with [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) and Java 21.

> **Early Access** - This project is in active development and considered experimental. While fully functional, APIs and tool definitions may change between releases. We encourage you to try it out, explore what's possible, and [open an issue](../../issues) or submit a pull request if you find ways to improve it. Your feedback and contributions help shape the future of AI-driven certificate management.

## Features

- **Inventory, configuration, and lifecycle tools** for certificates, keys, tokens, connectors, vaults, secrets,
  enrollment, permissions, discovery, compliance, approvals, signing, platform settings, and automation
- **Read-only by default**: access levels and toolsets decide which tools clients see
- **HTTP and STDIO transports**, with stateless HTTP for shared deployments
- **Shared OAuth authentication**: validated MCP tokens exchanged for separate ILM tokens, preserving each caller's identity
- **Local authentication**: client certificate and OAuth client credentials
- **AI-optimized responses**: formatted text output designed for LLM consumption
- **Lightweight Docker image**: multi-stage build with custom JRE (jlink)

## Inventory and Workflow Tools

Every tool works with ILM 2.17 or later unless it names a newer version. On an older ILM, such a tool answers with
the version it needs.

### Search

| Tool                    | Description                                                                     |
|-------------------------|---------------------------------------------------------------------------------|
| `get_searchable_fields` | Discover available filter fields and operators for any searchable resource type |

### Certificates

| Tool                      | Description                                                              |
|---------------------------|--------------------------------------------------------------------------|
| `get_statistics`          | Platform dashboard summary with certificate counts and breakdowns        |
| `search_certificates`     | Search certificates with filtering by CN, status, expiry, and pagination |
| `get_certificate`         | Detailed certificate information by UUID                                 |
| `validate_certificate`    | Validate a certificate and check results                                 |
| `get_certificate_chain`   | Full trust chain inspection                                              |
| `get_certificate_history` | Chronological event history for a certificate                            |

### Cryptographic Keys

| Tool          | Description                                                |
|---------------|------------------------------------------------------------|
| `search_keys` | Search keys with algorithm/status filtering and pagination |
| `get_key`     | Detailed key information by UUID                           |

### Infrastructure

| Tool                   | Description                                           |
|------------------------|-------------------------------------------------------|
| `list_authorities`     | Certificate authority instances                       |
| `list_ra_profiles`     | Registration Authority profiles                       |
| `list_connectors`      | Connector instances and their status                  |
| `list_groups`          | Groups for organizing certificates and keys           |
| `list_entities`        | Entity instances                                      |
| `list_credentials`     | Authentication credentials                            |
| `list_token_instances` | Cryptographic token instances (HSMs, software tokens) |
| `list_discoveries`     | Certificate discovery tasks and results               |
| `create_group`         | Create a group (`read-write`)                         |

### Secrets

| Tool                  | Description                              |
|-----------------------|------------------------------------------|
| `search_secrets`      | Search secrets with filtering            |
| `get_secret`          | Detailed secret information by UUID      |
| `get_secret_versions` | Secret version history                   |

### Vaults

| Tool                   | Description                              |
|------------------------|------------------------------------------|
| `list_vault_instances` | Vault backend instances                  |
| `list_vault_profiles`  | Vault profiles                           |

### Cryptographic Assets

| Tool                               | Description                                                                  |
|------------------------------------|------------------------------------------------------------------------------|
| `get_crypto_asset_statistics`      | PQC readiness overview of the cryptographic asset inventory (ILM 2.20+)      |
| `search_crypto_assets`             | Search assets by PQC verdict, type, and other fields (ILM 2.20+)             |
| `get_crypto_asset`                 | Asset details with its PQC verdict, properties, and source CBOMs (ILM 2.20+) |
| `explain_crypto_asset_pqc_verdict` | Rule-by-rule explanation of an asset's PQC verdict (ILM 2.20+)               |

### CBOMs

| Tool                 | Description                                                |
|----------------------|------------------------------------------------------------|
| `search_cboms`       | Search stored CBOMs with their asset counts and sync state |
| `get_cbom`           | CBOM details by UUID                                       |
| `get_cbom_versions`  | Stored versions of a CBOM                                  |
| `search_cbom_assets` | Cryptographic assets one CBOM contributed (ILM 2.20+)      |

### Signing

| Tool                      | Description                                                                    |
|---------------------------|--------------------------------------------------------------------------------|
| `search_signing_profiles` | Search signing profiles with their scheme, workflow, and protocols (ILM 2.18+) |
| `get_signing_profile`     | Signing profile details by UUID (ILM 2.18+)                                    |
| `search_tsp_profiles`     | Search TSP profiles with their signing URLs (ILM 2.18+)                        |
| `search_signing_records`  | Search signing records, without signature values or content (ILM 2.19+)        |
| `get_signing_statistics`  | Signing volume and breakdowns by profile, protocol, and scheme (ILM 2.19+)     |

### Workflows

| Tool                  | Description                                                                                        |
|-----------------------|----------------------------------------------------------------------------------------------------|
| `list_triggers`       | Workflow triggers with their event and resource                                                    |
| `get_trigger`         | One trigger in plain words: its event, conditions, outcome, and platform attachment               |
| `list_event_triggers` | Which platform triggers run when an event happens, in order                                        |
| `list_trigger_fields` | Fields a trigger's conditions can check and its setFields can set, with their values               |
| `create_trigger`      | Set up a whole workflow in one call and attach it to its event (`read-write`)                      |
| `attach_trigger`      | Attach a trigger to its event platform-wide (`read-write`)                                         |
| `detach_trigger`      | Detach a trigger from its event's platform-wide list (`read-write`)                                |
| `delete_trigger`      | Delete an unattached trigger with the parts created for it (`full`)                                |

### Custom Attributes

| Tool                      | Description                                                         |
|---------------------------|---------------------------------------------------------------------|
| `list_custom_attributes`  | Custom attributes with their content type, resources, and field ID  |
| `create_custom_attribute` | Create a custom attribute (`read-write`)                            |

### Users and Roles

| Tool         | Description                                        |
|--------------|----------------------------------------------------|
| `list_users` | Users with username, name, email, and UUID         |
| `list_roles` | Roles with name, UUID, and description             |

### Notifications

| Tool                                  | Description                                                                              |
|---------------------------------------|------------------------------------------------------------------------------------------|
| `list_notification_instances`         | Notification instances (email, webhook and other channels) with connector and kind       |
| `get_notification_connector_settings` | What a new instance needs: fields, defaults, choices and mappable recipient attributes   |
| `create_notification_instance`        | Create an instance, with connector defaults and callback fields filled in (`read-write`) |
| `delete_notification_instance`        | Delete an instance that no profile sends through (`full`)                                |
| `list_notification_profiles`          | Notification profiles with their recipients and instance                                 |
| `get_notification_profile`            | One profile in plain words: recipients, instance, in-app flag, frequency, repetitions    |
| `create_notification_profile`         | Create a profile: recipients, instance or in-app, and repeats (`read-write`)             |
| `delete_notification_profile`         | Delete a profile that no trigger sends through (`full`)                                  |

## Configuration and Lifecycle Tools

The server also supports the configuration areas below. MCP tool discovery supplies each operation's parameters and
minimum ILM version. See [configuration examples and update behavior](docs/configuration.md).

| Area | Operations |
|------|------------|
| Connectors and proxies | Authentication schemas, connection probes, registration, configuration, reconnect, approval and normal deletion; proxy provisioning |
| Vaults and secrets | Vault/profile configuration, typed secret creation and versions, ownership/groups, source/synchronization profiles and explicit content retrieval |
| Authorities and RA profiles | Provider schemas, instance/profile configuration, enrollment bindings and activation |
| Certificates | Requests and issuance, registration, renewal/rekey, revocation, manual completion/cancellation, import/export, metadata, relations and archival |
| Keys and tokens | Token instances/profiles, activation, key generation/import/export, metadata, usage, state transitions and destruction |
| Credentials | Provider schemas, creation, replacement, activation and deletion |
| Users, groups and roles | User/group configuration, memberships, roles, resource grants, object allow/deny grants and effective permissions |
| Discovery and workflows | Discovery creation, scheduling, results/control; ordered scoped triggers, history and trigger/notification updates |
| Compliance and approvals | Profiles, internal/provider rules and groups, associations, checks/results, approval policies and eligible-recipient decisions |
| Attributes and catalogs | Full custom definitions and individual values, global metadata/promotion, custom/system OIDs and trusted certificates |
| Entities and locations | Instance/location configuration and certificate deployment, removal and issuance |
| Signing and timestamping | Signing/TSP profiles, formatter settings, time quality and TSP basic credentials |
| Platform | Scheduler job configuration/state/history, platform/authentication/OAuth/logging/event settings |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Running ILM platform instance, version 2.17 or later

### Build

```bash
mvn clean verify
```

### Run in STDIO Mode

STDIO mode is used for direct integration with MCP clients like Claude Desktop or Claude Code.

```bash
java -jar target/mcp-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=stdio
```

### Run in HTTP Mode

HTTP mode exposes a Streamable HTTP endpoint for network-based MCP clients. For a shared remote service, use the
[stateless OAuth configuration](docs/shared-http-authentication.md) and the [Helm deployment](deploy/README.md).
The command below starts the existing HTTP profile; shared authentication requires the additional configuration.

```bash
java -jar target/mcp-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=http
```

The server starts on port 8080 by default.

## Configuration

### Environment Variables

#### ILM API Connection

| Variable          | Description                     | Default                 |
|-------------------|---------------------------------|-------------------------|
| `ILM_API_URL`     | ILM API base URL (up to `/api`) | `https://localhost/api` |
| `ILM_AUTH_METHOD` | Authentication method           | `certificate`           |

#### Authentication

**Shared OAuth** (`ILM_AUTH_METHOD=oauth-delegation`):

Enable `ILM_MCP_SECURITY_ENABLED=true` and `SPRING_AI_MCP_SERVER_PROTOCOL=STATELESS`. Each request must carry an
access token for the MCP resource. The server validates it and exchanges it through the configured issuer for an
ILM token for the same user. Select `standard` exchange or explicit `delegation`; the server never switches modes
after an error. See [all settings and identity guarantees](docs/shared-http-authentication.md), the
[example Keycloak integration](docs/keycloak/README.md), and the [deployment chart](deploy/charts/mcp-server/README.md).

**Client Certificate** (`ILM_AUTH_METHOD=certificate`):

| Variable         | Description            | Default      |
|------------------|------------------------|--------------|
| `ILM_SSL_BUNDLE` | Spring SSL bundle name | `ilm-client` |

Configure the SSL bundle in `application.yml` or via Spring Boot SSL bundle properties.

**OAuth Pass-through** (`ILM_AUTH_METHOD=oauth-passthrough`):

Legacy HTTP integration: forwards the incoming bearer token to ILM. Use shared OAuth for a shared remote MCP
server so the MCP and ILM resource audiences are validated separately.

**OAuth Client Credentials** (`ILM_AUTH_METHOD=oauth-client-credentials`):

| Variable                  | Description         | Required |
|---------------------------|---------------------|----------|
| `ILM_OAUTH_CLIENT_ID`     | OAuth client ID     | Yes      |
| `ILM_OAUTH_CLIENT_SECRET` | OAuth client secret | Yes      |
| `ILM_OAUTH_TOKEN_URL`     | Token endpoint URL  | Yes      |
| `ILM_OAUTH_SCOPE`         | OAuth scope         | No       |
| `ILM_OAUTH_AUDIENCE`      | OAuth audience      | No       |

#### Tool Access

| Variable           | Description                                       | Default     |
|--------------------|---------------------------------------------------|-------------|
| `ILM_MCP_ACCESS`   | Access level: `read-only`, `read-write` or `full` | `read-only` |
| `ILM_MCP_TOOLSETS` | `all`, or a comma-separated list of toolsets      | `all`       |

`read-only` offers ordinary reads. `read-write` adds reversible creation and configuration changes. `full` also
permits destructive operations and explicitly named sensitive-output tools such as `export_key`,
`export_certificate_keystore`, `get_secret_content`, `generate_acme_eab_key` and
`get_proxy_installation_instructions`. Sensitive reads retain truthful read-only MCP hints but are unavailable below
`full`. Ordinary responses omit protected material, including when ILM returns decrypted values.

Toolsets are `certificates`, `keys`, `infrastructure`, `secrets`, `vaults`, `crypto-assets`, `cboms`, `signing`,
`workflows`, `attributes`, `users`, `notifications`, `connectors`, `credentials`, `authorities`, `ra-profiles`,
`tokens`, `enrollment`, `entities`, `approvals`, `scheduler`, `settings`, `metadata`, `oids`, `trust`, `discovery`
and `compliance`. The original selectors remain available; `infrastructure` groups the original infrastructure
inventory with its configuration tools, and `users` includes group/role/permission administration. Some selectors
share components; selecting overlapping areas registers each tool once. Focused selectors include their relevant inventory lists. `get_searchable_fields` is always available. Unknown settings stop startup.

Attaching or detaching a trigger makes ILM re-save every platform trigger of that event to run as the identity that
made the change.

Shared HTTP uses `ILM_AUTH_METHOD=oauth-delegation`; ILM checks the authenticated user's permissions on every API
operation. The access level and toolsets only narrow that access. Existing HTTP write configurations also accept
the legacy `oauth-passthrough` mode. Certificate and client-credentials authentication over HTTP are restricted to
`read-only` and use the server's identity, so keep those endpoints on trusted networks. STDIO supports certificate
and OAuth client-credentials authentication with a dedicated, narrowly permitted ILM identity.

#### Logging

A broad setting such as `LOGGING_LEVEL_ROOT=DEBUG` or `TRACE` keeps request contents out of the logs, because the loggers that
print them are pinned in `logback-spring.xml`. Turning one of them on by name, for example
`LOGGING_LEVEL_IO_MODELCONTEXTPROTOCOL=DEBUG`, prints whole requests, including secret values.

#### Server

| Variable    | Description                       | Default |
|-------------|-----------------------------------|---------|
| `PORT`      | HTTP server port (HTTP mode only) | `8080`  |
| `JAVA_OPTS` | JVM options                       | (empty) |

## Docker

Images are published to `hub.omnitrustregistry.com/ilm/mcp-server`. `latest` is the newest release and
`develop-latest` the newest build of `main`:

```bash
docker pull hub.omnitrustregistry.com/ilm/mcp-server:latest
```

### Build

```bash
docker build -t ilm-mcp-server .
```

### Run

```bash
docker run \
  -e ILM_API_URL=https://ilm.example.invalid/api \
  -e ILM_AUTH_METHOD=oauth-client-credentials \
  -e ILM_OAUTH_CLIENT_ID=my-client \
  -e ILM_OAUTH_CLIENT_SECRET=my-secret \
  -e ILM_OAUTH_TOKEN_URL=https://identity.example.invalid/tenant/token \
  -p 8080:8080 \
  ilm-mcp-server
```

These reserved example URLs must be replaced with the deployment's API URL and the token endpoint from its issuer metadata.
The Docker image runs in HTTP mode by default.

## MCP Client Configuration

For a shared remote deployment, start with the [provider-neutral OAuth workflow](docs/shared-http-authentication.md).
The [Keycloak integration](docs/keycloak/README.md) is one provider-specific example. The examples below run a local STDIO server.

### Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "ilm": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-server-0.1.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ],
      "env": {
        "ILM_API_URL": "https://ilm.example.invalid/api",
        "ILM_AUTH_METHOD": "certificate"
      }
    }
  }
}
```

### Claude Code

Add to your `.mcp.json`:

```json
{
  "mcpServers": {
    "ilm": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-server-0.1.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio"
      ],
      "env": {
        "ILM_API_URL": "https://ilm.example.invalid/api",
        "ILM_AUTH_METHOD": "certificate"
      }
    }
  }
}
```

## Architecture

```
MCP Client (LLM) ──MCP──▶ MCP Tool (@McpTool) ──▶ Service (formatting) ──▶ IlmApiClient (RestClient) ──▶ ILM API
```

- **Tools** (`com.otilm.mcp.tool`) — MCP tool definitions with `@McpTool` annotations
- **Services** (`com.otilm.mcp.service`) — business logic and AI-friendly response formatting
- **Client** (`com.otilm.mcp.client`) — ILM API HTTP client using Spring `RestClient`
- **Config** (`com.otilm.mcp.config`) — Spring configuration and properties
- **Security** (`com.otilm.mcp.security`) — authentication token handling

## Development

### Run Tests

```bash
mvn test
```

Integration tests use [WireMock](https://wiremock.org/) to stub ILM API responses.

### Code Coverage

```bash
mvn verify
# Report at target/site/jacoco/index.html
```

### Formatting

`mvn verify` checks formatting and Checkstyle rules. To apply the formatting:

```bash
mvn spotless:apply
```

### Tech Stack

- Java 21
- Spring Boot 4.1.1 (parent `com.otilm:dependencies` 2.20.0)
- Spring AI 2.0.1
- ILM Interfaces 2.20.0 (`com.otilm:interfaces`)
- Jackson 3
- WireMock (testing)
- JaCoCo (coverage)
- Spotless and Checkstyle with the organization rules

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE.md](LICENSE.md) file for details.
