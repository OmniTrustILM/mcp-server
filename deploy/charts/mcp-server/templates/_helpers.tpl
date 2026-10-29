{{- define "mcp-server.name" -}}
{{- default .Chart.Name .Values.nameOverride | lower | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "mcp-server.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | lower | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := include "mcp-server.name" . -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mcp-server.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "mcp-server.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" | quote }}
{{ include "mcp-server.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- /*
A proxy URL as OutboundProxy reads it at startup, following java.net.URI: http://[user[:password]@]host:port, where a
path, query or fragment may follow the port and is ignored. The host is a name, an IPv4 address or a bracketed IPv6
address, and the port is 1 to 65535. Whitespace around the URL is ignored, and a blank value is no proxy. The server
stops at startup on any other URL, so this must stay equal to what OutboundProxy takes.
*/ -}}
{{- define "mcp-server.proxyUrlPattern" -}}
{{- $space := `[\t-\r\x{1c}-\x{1f} \x{1680}\x{2000}-\x{2006}\x{2008}-\x{200A}\x{2028}\x{2029}\x{205F}\x{3000}]` -}}
{{- $unreserved := `A-Za-z0-9_.!~*'()\-` -}}
{{- $escaped := `%[0-9A-Fa-f]{2}|[^\x00-\x7F\p{Z}\p{Cc}]` -}}
{{- $label := `[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?` -}}
{{- $topLabel := `[A-Za-z](?:[A-Za-z0-9-]*[A-Za-z0-9])?` -}}
{{- $octet := `0*(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])` -}}
{{- $ipv4 := print $octet `(?:\.` $octet `){3}` -}}
{{- $name := print `(?:(?:` $label `\.)+` $topLabel `|` $label `)\.?` -}}
{{- /* The IPv6address of RFC 3986: $h is a group, $g a group and its colon, $ls32 the last two groups. */ -}}
{{- $h := `[0-9A-Fa-f]{1,4}` -}}
{{- $g := print $h `:` -}}
{{- $ls32 := print `(?:` $h `:` $h `|` $ipv4 `)` -}}
{{- $ipv6 := print
    `(?:` $g `){6}` $ls32
    `|::(?:` $g `){5}` $ls32
    `|(?:` $h `)?::(?:` $g `){4}` $ls32
    `|(?:(?:` $g `){0,1}` $h `)?::(?:` $g `){3}` $ls32
    `|(?:(?:` $g `){0,2}` $h `)?::(?:` $g `){2}` $ls32
    `|(?:(?:` $g `){0,3}` $h `)?::` $g $ls32
    `|(?:(?:` $g `){0,4}` $h `)?::` $ls32
    `|(?:(?:` $g `){0,5}` $h `)?::` $h
    `|(?:(?:` $g `){0,6}` $h `)?::` -}}
{{- $host := print `(?:` $ipv4 `|` $name `|\[(?:` $ipv6 `)(?:%[A-Za-z0-9._]+)?\])` -}}
{{- $port := `0*(?:[1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])` -}}
{{- $userinfo := print `(?:[` $unreserved `;:&=+$,]|` $escaped `)*` -}}
{{- $path := print `(?:[` $unreserved `;:@&=+$,/]|` $escaped `)*` -}}
{{- $query := print `(?:[` $unreserved `;/?:@&=+$,\[\]]|` $escaped `)*` -}}
{{- $url := print `(?i:http)://(?:` $userinfo `@)?` $host `:` $port `(?:/` $path `)?(?:\?` $query `)?(?:#` $query `)?` -}}
{{- print `^` $space `*(?:` $url `)?` $space `*$` -}}
{{- end -}}

{{- /*
Umbrella mode is global.mcp.enabled, which the ILM chart sets. There the chart derives the settings that the ILM
chart's Services and bundled Keycloak determine, as README "Umbrella mode" lists them; a standalone install uses its
values as set. These helpers read .Values only, so the ILM chart can call them with this chart's values.
*/ -}}
{{- define "mcp-server.identityMode" -}}
{{- if .Values.global.mcp.enabled -}}
{{- .Values.global.mcp.identity.mode -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.host" -}}
{{- pluck "hostName" .Values.global .Values | compact | first | default "" -}}
{{- end -}}

{{- define "mcp-server.apiUrl" -}}
{{- if .Values.global.mcp.enabled -}}
{{- default "http://core-service:8080/api" .Values.apiUrl -}}
{{- else -}}
{{- .Values.apiUrl -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.resource" -}}
{{- if .Values.global.mcp.enabled -}}
{{- default (printf "https://%s/mcp" (include "mcp-server.host" .)) .Values.security.resource -}}
{{- else -}}
{{- .Values.security.resource -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.issuer" -}}
{{- if eq (include "mcp-server.identityMode" .) "managed" -}}
{{- printf "https://%s/kc/realms/ILM" (include "mcp-server.host" .) -}}
{{- else -}}
{{- .Values.security.issuer -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.jwkSetUri" -}}
{{- if eq (include "mcp-server.identityMode" .) "managed" -}}
{{- default "http://keycloak-internal-service:8080/kc/realms/ILM/protocol/openid-connect/certs" .Values.security.jwkSetUri -}}
{{- else -}}
{{- .Values.security.jwkSetUri -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.tokenUri" -}}
{{- if eq (include "mcp-server.identityMode" .) "managed" -}}
{{- default "http://keycloak-internal-service:8080/kc/realms/ILM/protocol/openid-connect/token" .Values.security.tokenUri -}}
{{- else -}}
{{- .Values.security.tokenUri -}}
{{- end -}}
{{- end -}}

{{- /* The Secret that holds the client secret, as JSON: {"name": ..., "key": ...}. */ -}}
{{- define "mcp-server.clientSecretRef" -}}
{{- if eq (include "mcp-server.identityMode" .) "managed" -}}
{{- $ref := .Values.global.mcp.clientSecretRef -}}
{{- dict "name" (default "mcp-server-secret" $ref.name) "key" (default "client-secret" $ref.key) | toJson -}}
{{- else -}}
{{- .Values.security.clientSecretRef | toJson -}}
{{- end -}}
{{- end -}}

{{- /* The hosts of the http:// URLs among the effective apiUrl, jwkSetUri and tokenUri, as a JSON list. */ -}}
{{- define "mcp-server.ownHosts" -}}
{{- $hosts := list -}}
{{- range $url := list (include "mcp-server.apiUrl" .) (include "mcp-server.jwkSetUri" .) (include "mcp-server.tokenUri" .) -}}
{{- if hasPrefix "http://" $url -}}
{{- $hosts = append $hosts (urlParse $url).hostname -}}
{{- end -}}
{{- end -}}
{{- $hosts | uniq | toJson -}}
{{- end -}}

{{- define "mcp-server.validate" -}}
{{- $mode := include "mcp-server.identityMode" . -}}
{{- $host := include "mcp-server.host" . -}}
{{- $security := .Values.security -}}
{{- $settings := dict "apiUrl" .Values.apiUrl
    "security.resource" $security.resource "security.issuer" $security.issuer
    "security.jwkSetUri" $security.jwkSetUri "security.tokenUri" $security.tokenUri
    "security.clientId" $security.clientId "security.clientSecretRef.name" $security.clientSecretRef.name
    "security.exchangeMode" $security.exchangeMode "security.exchangeScope" $security.exchangeScope
    "security.authorizationScopes" $security.authorizationScopes -}}
{{- if and .Values.global.mcp.enabled (not $host) -}}
{{- fail "global.hostName is required when global.mcp.enabled is true" -}}
{{- end -}}
{{- if eq $mode "managed" -}}
{{- if not .Values.global.keycloak.enabled -}}
{{- fail "global.mcp.identity.mode managed needs global.keycloak.enabled; use external for another provider" -}}
{{- end -}}
{{- /* The ILM chart's realm import defines these for MCP and cannot see a value set here. */ -}}
{{- range $key := list "security.resource" "security.issuer" "security.clientId" "security.clientSecretRef.name"
    "security.exchangeMode" "security.exchangeScope" "security.authorizationScopes" -}}
{{- if get $settings $key -}}
{{- fail (printf "%s cannot be set with global.mcp.identity.mode managed" $key) -}}
{{- end -}}
{{- end -}}
{{- else if eq $mode "external" -}}
{{- range $key := list "security.issuer" "security.jwkSetUri" "security.tokenUri" "security.clientId"
    "security.clientSecretRef.name" "security.exchangeMode" -}}
{{- if not (get $settings $key) -}}
{{- fail (printf "%s is required with global.mcp.identity.mode external" $key) -}}
{{- end -}}
{{- end -}}
{{- /* Plain http only to a Kubernetes Service, <service>.<namespace>.svc[.<domain>], as the ILM operator allows. */ -}}
{{- range $key := list "jwkSetUri" "tokenUri" -}}
{{- $url := get $security $key -}}
{{- if and (hasPrefix "http://" $url) (not (regexMatch `^http://([a-z0-9-]+[.]){2,}svc([.][a-z0-9-]+)*(:[0-9]{1,5})?(/[^#[:space:]]*)?$` $url)) -}}
{{- fail (printf "security.%s must be https, or http to a <service>.<namespace>.svc host" $key) -}}
{{- end -}}
{{- end -}}
{{- /* The bundled Keycloak serves under https://<host>/kc/ and is the managed mode's provider. */ -}}
{{- $issuer := lower $security.issuer -}}
{{- $keycloak := printf "https://%s" (lower $host) -}}
{{- if and .Values.global.keycloak.enabled
    (or (hasPrefix (print $keycloak "/kc/") $issuer) (hasPrefix (print $keycloak ":443/kc/") $issuer)) -}}
{{- fail "security.issuer: use global.mcp.identity.mode managed for the bundled Keycloak" -}}
{{- end -}}
{{- else -}}
{{- range $key := list "apiUrl" "security.resource" "security.issuer" "security.jwkSetUri" "security.tokenUri"
    "security.clientId" "security.clientSecretRef.name" -}}
{{- if not (get $settings $key) -}}
{{- fail (printf "%s is required" $key) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if eq (include "mcp-server.resource" .) .Values.security.apiAudience -}}
{{- fail "MCP resource and downstream ILM audience must differ" -}}
{{- end -}}
{{- $allowHttp := or .Values.global.mcp.enabled .Values.security.allowHttpBackchannel -}}
{{- range $url := list (include "mcp-server.apiUrl" .) (include "mcp-server.jwkSetUri" .) (include "mcp-server.tokenUri" .) -}}
{{- if and (hasPrefix "http://" $url) (not $allowHttp) -}}
{{- fail "security.allowHttpBackchannel must be true for http backchannel URLs" -}}
{{- end -}}
{{- end -}}
{{- $proxyUrlPattern := include "mcp-server.proxyUrlPattern" . -}}
{{- range $key := list "httpProxy" "httpsProxy" -}}
{{- if not (mustRegexMatch $proxyUrlPattern (pluck $key $.Values.global $.Values | compact | first | default "")) -}}
{{- fail (printf "%s must be an http://host:port URL for the MCP server" $key) -}}
{{- end -}}
{{- end -}}
{{- $labels := include "ilm-lib.tplvalues.render" (dict "value" .Values.podLabels "context" $) | fromYaml -}}
{{- range $name := list "app.kubernetes.io/name" "app.kubernetes.io/instance" -}}
{{- if hasKey $labels $name -}}
{{- fail (printf "podLabels cannot override selector label %s" $name) -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "mcp-server.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "mcp-server.image" -}}
{{- $image := mergeOverwrite (deepCopy .Values.image) (dict "tag" (default .Chart.AppVersion .Values.image.tag)) -}}
{{- include "ilm-lib.images.image" (dict "image" $image "global" .Values.global) -}}
{{- end -}}

{{- define "mcp-server.pullSecrets" -}}
{{- include "ilm-lib.images.pullSecrets" (dict "images" (list .Values.image) "global" .Values.global) -}}
{{- end -}}

{{- define "mcp-server.customization.initContainers" -}}
{{- include "ilm-lib.customizations.render.yaml" (dict "parts" (list (concat .Values.global.initContainers .Values.initContainers)) "context" $) -}}
{{- end -}}

{{- define "mcp-server.customization.volumes" -}}
{{- include "ilm-lib.customizations.render.yaml" (dict "parts" (list (concat .Values.global.additionalVolumes .Values.additionalVolumes)) "context" $) -}}
{{- end -}}

{{- define "mcp-server.customization.volumeMounts" -}}
{{- include "ilm-lib.customizations.render.yaml" (dict "parts" (list (concat .Values.global.additionalVolumeMounts .Values.additionalVolumeMounts)) "context" $) -}}
{{- end -}}

{{- define "mcp-server.customization.env" -}}
{{- $env := include "ilm-lib.customizations.render.yaml" (dict "parts" (list (concat .Values.global.additionalEnv.variables .Values.additionalEnv.variables)) "context" $) -}}
{{- $seen := dict -}}
{{- range $name := list "SPRING_PROFILES_ACTIVE" "SPRING_AI_MCP_SERVER_TYPE" "SPRING_AI_MCP_SERVER_PROTOCOL" "PORT" "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED" "ILM_AUTH_METHOD" "ILM_API_URL" "ILM_MCP_ACCESS" "ILM_MCP_TOOLSETS" "ILM_MCP_SECURITY_ENABLED" "ILM_MCP_SECURITY_ISSUER" "ILM_MCP_SECURITY_RESOURCE" "ILM_MCP_SECURITY_JWK_SET_URI" "ILM_MCP_SECURITY_TOKEN_URI" "ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL" "ILM_MCP_SECURITY_CLIENT_ID" "ILM_MCP_SECURITY_CLIENT_SECRET" "ILM_MCP_SECURITY_API_AUDIENCE" "ILM_MCP_SECURITY_EXCHANGE_MODE" "ILM_MCP_SECURITY_EXCHANGE_SCOPE" "ILM_MCP_SECURITY_REQUIRED_SCOPE" "ILM_MCP_SECURITY_AUTHORIZATION_SCOPES" "ILM_MCP_SECURITY_ALLOWED_ORIGINS" "ILM_MCP_SECURITY_JWS_ALGORITHMS" "TRUSTED_CERTIFICATES" "HTTP_PROXY" "HTTPS_PROXY" "NO_PROXY" -}}
{{- $_ := set $seen $name true -}}
{{- end -}}
{{- range ($env | fromYamlArray) -}}
{{- $name := .name | default "" -}}
{{- if not (regexMatch "^[A-Za-z_][A-Za-z0-9_]*$" $name) -}}
{{- fail "additionalEnv.variables must render valid environment variable names" -}}
{{- end -}}
{{- if hasKey $seen $name -}}
{{- fail (printf "additionalEnv.variables cannot duplicate environment variable %s" $name) -}}
{{- end -}}
{{- $_ := set $seen $name true -}}
{{- end -}}
{{- $env -}}
{{- end -}}

{{- define "mcp-server.customization.envFrom" -}}
{{- include "ilm-lib.customizations.render.configMapEnv" (dict "parts" (list .Values.global.additionalEnv.configMaps .Values.additionalEnv.configMaps) "context" $) -}}
{{- include "ilm-lib.customizations.render.secretEnv" (dict "parts" (list .Values.global.additionalEnv.secrets .Values.additionalEnv.secrets) "context" $) -}}
{{- end -}}

{{- /*
The HTTP_PROXY, HTTPS_PROXY and NO_PROXY entries of the container's env, each only when set; a global value takes
precedence over the chart's own. While a proxy is set, the hosts in "noProxyHosts" are appended to NO_PROXY, except
for those it already lists. Call it with (dict "context" . "noProxyHosts" (list "core" ...)).
*/ -}}
{{- define "mcp-server.proxyEnv" -}}
{{- $values := .context.Values -}}
{{- $httpProxy := pluck "httpProxy" $values.global $values | compact | first -}}
{{- $httpsProxy := pluck "httpsProxy" $values.global $values | compact | first -}}
{{- $noProxy := pluck "noProxy" $values.global $values | compact | first | default "" -}}
{{- if or $httpProxy $httpsProxy -}}
{{- $listed := regexSplit `\s*[,|]\s*` (lower (trim $noProxy)) -1 -}}
{{- range $host := .noProxyHosts -}}
{{- if not (has (lower $host) $listed) -}}
{{- $listed = append $listed (lower $host) -}}
{{- $noProxy = join "," (compact (list $noProxy $host)) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- with $httpProxy }}
- name: HTTP_PROXY
  value: {{ . | quote }}
{{- end }}
{{- with $httpsProxy }}
- name: HTTPS_PROXY
  value: {{ . | quote }}
{{- end }}
{{- with $noProxy }}
- name: NO_PROXY
  value: {{ . | quote }}
{{- end }}
{{- end -}}
