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

{{- define "mcp-server.validate" -}}
{{- if eq .Values.security.resource .Values.security.apiAudience -}}
{{- fail "MCP resource and downstream ILM audience must differ" -}}
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
{{- range $name := list "SPRING_PROFILES_ACTIVE" "SPRING_AI_MCP_SERVER_TYPE" "SPRING_AI_MCP_SERVER_PROTOCOL" "PORT" "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED" "ILM_AUTH_METHOD" "ILM_API_URL" "ILM_MCP_ACCESS" "ILM_MCP_TOOLSETS" "ILM_MCP_SECURITY_ENABLED" "ILM_MCP_SECURITY_ISSUER" "ILM_MCP_SECURITY_RESOURCE" "ILM_MCP_SECURITY_JWK_SET_URI" "ILM_MCP_SECURITY_TOKEN_URI" "ILM_MCP_SECURITY_CLIENT_ID" "ILM_MCP_SECURITY_CLIENT_SECRET" "ILM_MCP_SECURITY_API_AUDIENCE" "ILM_MCP_SECURITY_EXCHANGE_MODE" "ILM_MCP_SECURITY_EXCHANGE_SCOPE" "ILM_MCP_SECURITY_REQUIRED_SCOPE" "ILM_MCP_SECURITY_AUTHORIZATION_SCOPES" "ILM_MCP_SECURITY_ALLOWED_ORIGINS" "ILM_MCP_SECURITY_JWS_ALGORITHMS" -}}
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
