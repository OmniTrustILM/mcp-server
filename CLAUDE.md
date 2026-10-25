# ILM MCP Server

## Project Overview

MCP (Model Context Protocol) server for the ILM platform (formerly CZERTAINLY). Exposes ILM certificate and key management operations as AI-friendly tools using Spring AI.

## Tech Stack

- Java 21
- Spring Boot 4.1.1 through the parent `com.otilm:dependencies` 2.20.0
- Spring AI 2.0.1 (MCP Java SDK 2.0)
- Maven
- ILM Interfaces library (`com.otilm:interfaces` 2.20.0)
- Jackson 3 (`tools.jackson`)
- Supported ILM platform: 2.17 or later

## Build & Run

### Prerequisites

- Java 21+
- Maven 3.9+

### Build

```bash
mvn clean verify
```

`verify` also runs the organization's Spotless and Checkstyle gates. Format before committing:

```bash
mvn spotless:apply
```

### Run locally (STDIO mode)

```bash
java -jar target/mcp-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=stdio
```

### Run locally (HTTP mode)

```bash
java -jar target/mcp-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=http
```

### Run tests

```bash
mvn test
```

### Check code coverage

```bash
mvn verify
# Report at target/site/jacoco/index.html
```

### Local SonarQube analysis

```bash
./scripts/sonar-local.sh
```

Runs `mvn verify`, analyses the project with the pull-request workflow's scope in an ephemeral SonarQube container (Docker required), and fails unless the quality gate passes (ratings A, hotspots reviewed, coverage >= 80%, duplication <= 3%) and no open issue touches a line changed since `origin/main`.

The local analysis also runs chart-script tests with Python coverage. Python dependencies are pinned with wheel hashes; use Python 3.12 and Helm for these checks. Keep production scripts in the analysis scope and import both JaCoCo and Python coverage reports.

### Quality and review rules

- Coverage must be at least 80%, including SonarCloud coverage on new code; duplication must be below 3%. Address valid SonarCloud issues and review findings. A local overall gate does not establish that the Cloud pull-request gate passed: inspect the checks and findings for the current commit after each push.
- Review each change with the local Copilot CLI using a different model family and a model/effort at least as strong as the implementation. Apply the organization's `pr-hygiene` skill. Skip nitpicks that add noise or complexity without improving the code.
- Keep local design and planning documents ignored by Git. Do not add TODO/FIXME markers, planning references, sensitive values or deployment-specific details to shipped code or comments.
- Keep descriptions and comments simple, accurate and properly formatted. Commit and PR descriptions contain only what changed, with no AI attribution, co-author lines, test notes or plans.
- Obtain explicit approval before pushing or opening a PR. Squash before the first push; keep later fixes as normal DCO-signed commits. Prefer the simplest maintainable fix, and clarify material design uncertainty before implementing it.

## Configuration

### ILM API Connection

Set via environment variables or application.yml:

- `ILM_API_URL` - ILM API base URL (up to /api)
- `ILM_AUTH_METHOD` - Authentication method: `certificate`, `oauth-passthrough`, `oauth-client-credentials`, or `oauth-delegation`

### Authentication

**Shared OAuth (HTTP):**
- `oauth-delegation` with `ILM_MCP_SECURITY_ENABLED=true`, SYNC and STATELESS HTTP
- Validate the caller's MCP-audience token, then exchange for an ILM-audience token for the same subject
- Explicit `standard` or `delegation` mode; no passthrough, service-identity or automatic mode fallback
- Request-local credentials travel through the SDK transport context; no user sessions, grants or token cache
- HTTPS, bounded timeouts and no redirects on ILM/JWK/token clients; sanitize response parsing failures as well as request failures
- See [shared HTTP authentication](docs/shared-http-authentication.md) and [example Keycloak integration](docs/keycloak/README.md)

**Client Certificate:**
- `ILM_SSL_BUNDLE` - SSL bundle name (default: `ilm-client`)
- Configure Spring SSL bundles in application.yml

**OAuth Pass-through (HTTP mode):**
- Client sends Bearer token, server forwards to ILM API

**OAuth Client Credentials:**
- `ILM_OAUTH_CLIENT_ID`, `ILM_OAUTH_CLIENT_SECRET`, `ILM_OAUTH_TOKEN_URL`
- `ILM_OAUTH_SCOPE`, `ILM_OAUTH_AUDIENCE` (optional)

### Tool Access

- `ILM_MCP_ACCESS` - `read-only` (default), `read-write` or `full`
- `ILM_MCP_TOOLSETS` - `all` (default) or a comma-separated list of toolsets (see `Toolset`)
- Above `read-only`, HTTP mode requires `oauth-delegation` or legacy `oauth-passthrough`; the server refuses to start otherwise

### Deployment

- Keep the standalone Helm chart in `deploy/charts/mcp-server` in this repository
- Reuse the pinned `ilm-lib` dependency and application-chart values conventions; keep the chart compatible with parent dependency conditions and shared globals
- Chart checks and signed OCI publication use `.github/workflows/test_chart.yaml` and `publish_chart.yaml`
- Build locked dependencies, run `helm lint deploy/charts/mcp-server --strict --values deploy/charts/mcp-server/ci/default-values.yaml` and the tests under `deploy/tests` after chart changes
- Shared deployments require an existing credential Secret, explicit network peers and stateless transport; pin reviewed image digests before allowing traffic
- Keep repository deployment examples generic: use reserved `https://ilm.example.invalid/mcp`, `https://ilm.example.invalid/api` and `https://identity.example.invalid/tenant`; keep real deployment endpoints, namespaces and operator paths in private deployment configuration
- Main keeps the chart's development version; release tags carry release versions
- Publish the `0.0.0` development alias only after successful mainline chart publication and signing; retain immutable versioned artifacts

## Architecture

```
MCP Tool (@McpTool) → Service (formatting) → IlmApiClient (RestClient) → ILM API
```

- **Package**: `com.otilm.mcp`
- **Tools**: `com.otilm.mcp.tool` — MCP tool definitions
- **Services**: `com.otilm.mcp.service` — business logic + AI-friendly response formatting
- **Client**: `com.otilm.mcp.client` — ILM API HTTP client
- **Config**: `com.otilm.mcp.config` — Spring configuration
- **Security**: `com.otilm.mcp.security` — auth token handling

## Conventions

- Follow the OmniTrustILM Java repository conventions
- Service interfaces with `*Impl` suffix for implementations
- WireMock for integration tests (no Mockito)
- 80%+ test coverage, <3% code duplication
- AI-friendly formatted text responses from all tools (not raw JSON)
- Every tool is an `@McpTool` that states its kind through the hints: `readOnlyHint = true` for reads, `destructiveHint = false` for changes another change can undo. A tool that states neither counts as destructive and only appears at `full`
- Each `Toolset` contains the classes for its area; compatibility aggregates can overlap focused selectors and registration deduplicates classes. `McpToolConfiguration` is the only place tools are registered, so Spring AI's annotation scanner and tool-callback converter stay off
- Logs carry identifiers such as UUIDs at most, never request bodies or other argument values
- Services build their text with `TextFormat`, wrap ILM calls in `ToolResponses.run(...)` so errors come back as readable text, and build search requests with `SearchRequests.of(...)`
- The minimum supported ILM is 2.17. A tool that needs a newer ILM gets an `IlmFeature` entry with its minimum version, runs through `PlatformVersionCheck.run(...)`, and names that version in its description
- Jackson 3 only: no imports from Jackson 2 `databind`, `core`, `datatype`, `dataformat` or `module` packages (`com.fasterxml.jackson.annotation` is shared and allowed)
- Every `RestClient` to ILM comes from `IlmApiClient.restClientBuilder(...)`, which reads responses leniently so older ILM versions parse; tests use `IlmTestSupport.client(...)`
- Connector attribute definitions and callback answers are read into the server's own records (`ConnectorAttribute`), because the interfaces' attribute classes need Jackson 2
- Test fixtures hold synthetic values only, with canonical UUIDs; responses recorded from ILM 2.20 live in `src/test/resources/fixtures/v2_20`
- Write tools validate their input before calling ILM and answer with what they created or changed, including UUIDs; `create_trigger` removes what it created when a later step fails
- Commits carry a DCO sign-off (`git commit -s`)

## Docker

Images are published to `hub.omnitrustregistry.com/ilm/mcp-server` by the shared organization workflows.

```bash
docker build -t ilm-mcp-server .
docker run -e ILM_API_URL=https://ilm.example.invalid/api -e ILM_AUTH_METHOD=oauth-passthrough -p 8080:8080 ilm-mcp-server
```
