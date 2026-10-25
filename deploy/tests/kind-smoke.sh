#!/usr/bin/env bash
# Requires a dedicated kind cluster named chart-testing; never targets an existing user cluster.
set -euo pipefail
cd "$(dirname "$0")/../.."
context=kind-chart-testing
node=chart-testing-control-plane
image=hub.omnitrustregistry.com/ilm/mcp-server
work=$(mktemp -d)
forward_pid=
cleanup() {
  if [[ -n "$forward_pid" ]]; then kill "$forward_pid" 2>/dev/null || true; fi
  rm -rf "$work"
}
trap cleanup EXIT
check_application_args() {
  kubectl --context "$context" --namespace chart-testing exec deployment/mcp-smoke -- cat /proc/1/cmdline > "$work/argv"
  python3 - "$work/argv" <<'PY'
from pathlib import Path
import sys
assert b'--server.port=8080' in Path(sys.argv[1]).read_bytes().split(b'\0'), 'default entrypoint did not forward image.args'
PY
}
start_forward() {
  kubectl --context "$context" --namespace chart-testing port-forward service/mcp-smoke 18080:8080 >"$work/forward.log" 2>&1 &
  forward_pid=$!
  for ((attempt=0; attempt<30; attempt++)); do
    if curl --silent --fail --max-time 2 http://127.0.0.1:18080/actuator/health/readiness > /dev/null; then return; fi
    sleep 1
  done
  cat "$work/forward.log"
  return 1
}

docker build -t "$image:chart-ci" .
kind load docker-image "$image:chart-ci" --name chart-testing
# Read the manifest digest from containerd, not Docker's config/image ID. Register
# the repository@digest alias explicitly so Never resolves it entirely offline.
digest=$(docker exec "$node" ctr --namespace k8s.io images list |
  awk -v image="$image:chart-ci" '$1 == image {print $3}')
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
docker exec "$node" ctr --namespace k8s.io images tag --force "$image:chart-ci" "$image@$digest"
kubectl --context "$context" create namespace chart-testing
kubectl --context "$context" --namespace chart-testing create secret generic mcp-smoke-oauth \
  --from-literal=client-secret=synthetic-not-a-real-credential
helm upgrade --install mcp-smoke deploy/charts/mcp-server \
  --kube-context "$context" --namespace chart-testing \
  --values deploy/charts/mcp-server/ci/default-values.yaml --set-string "image.digest=$digest" \
  --set-json 'image.args=["--server.port=8080"]' \
  --set-json 'security.authorizationScopes=["openid"]' --wait --timeout 5m
kubectl --context "$context" --namespace chart-testing rollout status deployment/mcp-smoke --timeout=60s
check_application_args
start_forward
python3 deploy/tests/smoke_http.py
kill "$forward_pid"
wait "$forward_pid" 2>/dev/null || true
forward_pid=
# Delegation mode, a second replica and a rollout must work without a persistent/session store.
helm upgrade mcp-smoke deploy/charts/mcp-server \
  --kube-context "$context" --namespace chart-testing \
  --values deploy/charts/mcp-server/ci/default-values.yaml --set-string "image.digest=$digest" \
  --set-json 'image.args=["--server.port=8080"]' \
  --set replicaCount=2 --set security.exchangeMode=delegation \
  --set-json 'security.authorizationScopes=["openid","mcp:delegate"]' --wait --timeout 5m
kubectl --context "$context" --namespace chart-testing rollout restart deployment/mcp-smoke
kubectl --context "$context" --namespace chart-testing rollout status deployment/mcp-smoke --timeout=180s
check_application_args
start_forward
python3 deploy/tests/smoke_http.py openid mcp:delegate
