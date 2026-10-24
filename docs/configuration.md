# Configuration and lifecycle operations

Start with the platform inventory and the relevant provider schema tool. Provider schemas determine the actual
attribute names, types, defaults and offered choices for the selected connector/profile. Configuration tools use
fixed ILM endpoints and return readable metadata and identifiers.

## Attribute values

A configuration argument is a JSON object encoded as an MCP string. `attributes`, `customAttributes` and
`authAttributes` contain attribute names mapped to typed values. For example, a connector whose basic-authentication
schema declares username as a string and password as a v2 secret accepts:

```json
{
  "name": "Issuing authority connector",
  "version": "v2",
  "url": "https://connector.example.test",
  "authType": "basic",
  "authAttributes": {
    "username": "operator",
    "password": {"secret": "fresh-value", "protectionLevel": "none"}
  }
}
```

Pass this JSON as `configuration` to `create_connector`. Read its schema first with
`get_connector_authentication_attributes`. `test_connector_connection` probes the same connection without registering
it. Active connection probes require at least `read-write` access. Connector URLs cannot embed user credentials.
A connection update requires a replacement URL/authentication and an explicit `proxyUuid` or `noProxy=true`; names
and framework versions cannot be changed.

The shared encoder supports published v2/v3 scalar, secret, file, code, credential, object and resource variants.
Secrets use their schema's object shape; files carry Base64 content and file metadata; list choices can select an
offered data value or a `{ "reference": "offered-reference" }` object. An explicit empty array clears an optional list or
multiple-value selection; required selections must retain a value. Callback dependencies are resolved through the
selected connector or parent resource. Unsupported types, unknown attribute names, masks and unresolved/cyclic
callbacks are rejected before the mutation.

## Updates and associations

Convenience updates retain omitted readable fields and attribute values before submitting ILM replacement requests.
If an existing value is protected, masked or otherwise unavailable, supply it freshly; the update stops instead of
silently clearing it. Provider changes may require a complete replacement configuration. Explicit complete-list tools
state that behavior in their descriptions; those with `replace` require `true`, and an empty list intentionally removes
all entries. Prefer targeted membership, grant, rule and association tools when changing one item.

Custom attribute definitions support display flags, required/read-only/list settings, protection, predefined content
and resource associations through `create_custom_attribute_definition` and `update_custom_attribute`.
`set_custom_attribute_value` changes one object's attribute using the resource schema and preserves unrelated values.
Use `delete_custom_attribute_value` at `full` access to clear an object's custom attribute; the setter requires a
nonempty value.

Platform and scoped trigger association writes re-save every included association to run as the caller. They preserve
other entries and order and disclose that identity change. Approval decisions record the eligible recipient's decision;
certificate or secret work may remain pending after the decision is recorded. Discovery and certificate operations
also report pending/asynchronous outcomes without claiming the underlying work has completed.

## Access and protected output

The default access level is `read-only`. `read-write` adds reversible changes, and `full` adds deletion, revocation,
destruction and explicit material-returning tools. HTTP access above read-only requires OAuth pass-through so ILM
applies the caller's authorization. ILM permissions and dependency checks remain in force at every MCP level.

Only explicit full-access tools return private key/keystore exports, secret content, ACME EAB keys or proxy provisioning
instructions. These operations retain their actual read/write MCP hints. Ordinary detail, schema, list and update
responses exclude secret/file/credential values and key material. Error messages suppress upstream bodies; ordinary
URL displays omit credentials, query strings and fragments. Ordinary custom-attribute details show names only:
ILM response attributes do not include protection settings, so their values and references are omitted.
Request/response contents are not logged under the provided logging configuration.

## Version support and boundaries

ILM 2.17 is the baseline. Tools and fields with later requirements identify the minimum version and check the connected
platform before use. If the version cannot be read, ILM answers the request directly.

| Version | Selected additions |
|---------|--------------------|
| 2.18 | Connector proxies, signing/TSP/time quality, trusted certificates and manual certificate lifecycle controls |
| 2.19 | Certificate registration/capabilities, RA request settings and authority interfaces, TSP authentication/basic credentials, signing record policy, system OIDs and certificate-extension OIDs |
| 2.20 | Key transfer and modern token schemas, extended certificate transfer/renewal/provider fields, extended discovery/enrollment/signing fields and CBOM synchronization settings |

Provider capabilities still determine whether an individual lifecycle operation is available. Key exports require an
active, enabled, exportable private/secret key item and profile/provider support. Scheduler edits change the Quartz cron
expression of an existing job; the API provides no generic create or run-now endpoint.

There is no native key-rotation operation in the supported API. Coordinate generation, certificate rekey and deployment
through their explicit tools. EST/PQC configuration CRUD, raw sign/decrypt execution, force/cascade fallbacks, privileged
approval overrides, CBOM ingestion/repair and UI preferences are outside this configuration interface.
