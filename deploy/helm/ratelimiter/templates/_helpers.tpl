{{- define "ratelimiter.image" -}}
{{ .root.Values.image.registry }}/ratelimiter-{{ .name }}:{{ .root.Values.image.tag | default .root.Chart.AppVersion }}
{{- end -}}

{{- define "ratelimiter.secretName" -}}
{{ .Values.existingSecret | default "ratelimiter" }}
{{- end -}}

{{- define "ratelimiter.securityContext" -}}
runAsNonRoot: true
runAsUser: 10001
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}
