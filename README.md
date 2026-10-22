# ILM MCP Server

[Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server for the [ILM](https://docs.otilm.com) platform. Exposes certificate and key management operations as AI-friendly tools, enabling LLMs and AI agents to interact with ILM through natural language.

Built with [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) and Java 21.

> **Early Access** - This project is in active development and considered experimental. While fully functional, APIs and tool definitions may change between releases. We encourage you to try it out, explore what's possible, and [open an issue](../../issues) or submit a pull request if you find ways to improve it. Your feedback and contributions help shape the future of AI-driven certificate management.

## Features

- **35 read-only tools** for certificates, cryptographic keys, infrastructure, secrets, vaults, signing, CBOMs, and
  the cryptographic asset inventory with its post-quantum (PQC) readiness
- **Read-only by default**: access levels and toolsets decide which tools clients see
- **Two transport modes**: HTTP (Streamable HTTP) and STDIO
- **Three authentication methods**: client certificate, OAuth pass-through, OAuth client credentials
- **AI-optimized responses**: formatted text output designed for LLM consumption
- **Lightweight Docker image**: multi-stage build with custom JRE (jlink)

## Available Tools

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

HTTP mode exposes a Streamable HTTP endpoint for network-based MCP clients.

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

**Client Certificate** (`ILM_AUTH_METHOD=certificate`):

| Variable         | Description            | Default      |
|------------------|------------------------|--------------|
| `ILM_SSL_BUNDLE` | Spring SSL bundle name | `ilm-client` |

Configure the SSL bundle in `application.yml` or via Spring Boot SSL bundle properties.

**OAuth Pass-through** (`ILM_AUTH_METHOD=oauth-passthrough`):

No additional configuration needed. The server forwards the Bearer token from incoming HTTP requests to the ILM API. Only works in HTTP mode.

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

`read-only` offers only tools that read. `read-write` adds tools that create or change things, and `full` adds tools
that delete, revoke or destroy. The toolsets are `certificates`, `keys`, `infrastructure`, `secrets`, `vaults`,
`crypto-assets`, `cboms` and `signing`; `get_searchable_fields` is always available. Tools outside the access level or
the toolsets are not registered, so clients never see them. Each tool tells the client whether it reads, writes or
destroys, so the client can ask before running it. An unknown value stops the server at startup.

Above `read-only`, HTTP mode requires `ILM_AUTH_METHOD=oauth-passthrough`, and the server refuses to start otherwise.
With a client certificate or client credentials, the HTTP endpoint has no login of its own, and every caller would act
as the server's ILM identity. STDIO mode works with every authentication method.

The server can never do more than the ILM identity it uses. Give that identity a dedicated ILM role with only the
permissions the enabled toolsets need. In HTTP mode without OAuth pass-through, keep the endpoint reachable from
trusted networks only, even at `read-only`.

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
  -e ILM_API_URL=https://my-instance.example.com/api \
  -e ILM_AUTH_METHOD=oauth-client-credentials \
  -e ILM_OAUTH_CLIENT_ID=my-client \
  -e ILM_OAUTH_CLIENT_SECRET=my-secret \
  -e ILM_OAUTH_TOKEN_URL=https://auth.example.com/token \
  -p 8080:8080 \
  ilm-mcp-server
```

The Docker image runs in HTTP mode by default.

## MCP Client Configuration

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
        "ILM_API_URL": "https://my-instance.example.com/api",
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
        "ILM_API_URL": "https://my-instance.example.com/api",
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

### Local SonarQube Analysis

Runs the build, analyses it with the pull-request workflow's scope in an ephemeral SonarQube container, and fails on a failed quality gate or on open issues touching changed lines. Requires Docker.

```bash
./scripts/sonar-local.sh
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
