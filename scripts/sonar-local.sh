#!/usr/bin/env bash
# Local SonarQube analysis for this Maven project.
# Starts an ephemeral SonarQube Community container, runs mvn verify with JaCoCo
# coverage, analyses the project with the pull-request workflow's scope using the
# Maven scanner, and fails unless the quality gate passes and no open issue
# touches a line changed since the base branch (committed or not). An issue
# touches a changed line when its own range or any secondary location covers
# it, so a finding already present in a changed method counts too. SonarQube
# Community has no shell analyzer, so shell scripts are checked on SonarCloud
# only.
# Prereqs: docker, mvn, curl, python3, git, openssl
# Usage: ./scripts/sonar-local.sh   (BASE_BRANCH defaults to origin/main)
set -euo pipefail

SONAR_PORT="${SONAR_PORT:-9000}"
BASE_BRANCH="${BASE_BRANCH:-origin/main}"
SONAR_MAVEN_PLUGIN="org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211"
CONTAINER_NAME="mcp-server-sonarqube-${SONAR_PORT}"
SONAR_URL="http://127.0.0.1:${SONAR_PORT}"
QG_NAME="local-overall"

cleanup() {
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

api() {
    curl -sf -u "${SONAR_CREDS}" "$@"
}

json() {
    local expression="$1"
    python3 -c "import json, sys; d = json.load(sys.stdin); print(${expression})"
}

# Prints every page of open issues, one JSON document per line.
issue_pages() {
    local page=1 body
    while :; do
        body=$(api "${SONAR_URL}/api/issues/search?componentKeys=${PROJECT_KEY}&resolved=false&ps=500&p=${page}" \
            | json "json.dumps(d)")
        echo "${body}"
        if [[ "$(echo "${body}" | json "d['paging']['pageIndex'] * d['paging']['pageSize'] >= d['paging']['total']")" == "True" ]]; then
            return
        fi
        page=$((page + 1))
    done
}

cleanup
echo "Starting SonarQube Community on port ${SONAR_PORT}..."
docker run -d --name "${CONTAINER_NAME}" -p "127.0.0.1:${SONAR_PORT}:9000" sonarqube:community >/dev/null

for i in $(seq 1 120); do
    if curl -sf "${SONAR_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
        break
    fi
    if [[ "$i" -eq 120 ]]; then
        echo "ERROR: SonarQube did not start within 2 minutes." >&2
        exit 1
    fi
    sleep 1
done

# The container is ephemeral; its admin password is generated per run and dies with it.
ADMIN_PASSWORD="$(openssl rand -hex 12)Aa1!"
curl -sf -o /dev/null -u admin:admin -X POST \
    "${SONAR_URL}/api/users/change_password?login=admin&previousPassword=admin&password=${ADMIN_PASSWORD}"
SONAR_CREDS="admin:${ADMIN_PASSWORD}"
TOKEN=$(api -X POST "${SONAR_URL}/api/user_tokens/generate?name=local-$(date +%s)" | json "d['token']")

echo "Configuring quality gate '${QG_NAME}' (ratings A, hotspots reviewed, coverage >= 80%, duplication <= 3%)..."
api -o /dev/null -X POST "${SONAR_URL}/api/qualitygates/create?name=${QG_NAME}"
for CONDITION in \
    "metric=reliability_rating&op=GT&error=1" \
    "metric=security_rating&op=GT&error=1" \
    "metric=sqale_rating&op=GT&error=1" \
    "metric=security_hotspots_reviewed&op=LT&error=100" \
    "metric=coverage&op=LT&error=80" \
    "metric=duplicated_lines_density&op=GT&error=3"; do
    api -o /dev/null -X POST "${SONAR_URL}/api/qualitygates/create_condition?gateName=${QG_NAME}&${CONDITION}"
done
api -o /dev/null -X POST "${SONAR_URL}/api/qualitygates/set_as_default?name=${QG_NAME}"

echo "Running mvn verify with JaCoCo..."
mvn -B -U verify

echo "Running the Sonar scanner..."
rm -f target/sonar/report-task.txt
# Sources, exclusions and tests match .github/workflows/sonar.yml. The org's own
# workflow references follow moving refs by design; their pinning findings are
# accepted on SonarCloud, so they are ignored here as well.
mvn -B "${SONAR_MAVEN_PLUGIN}:sonar" \
    -Dsonar.host.url="${SONAR_URL}" \
    -Dsonar.token="${TOKEN}" \
    -Dsonar.sources=. \
    "-Dsonar.exclusions=src/test/**,target/**" \
    -Dsonar.tests=src/test/java \
    -Dsonar.issue.ignore.multicriteria=e1 \
    -Dsonar.issue.ignore.multicriteria.e1.ruleKey=githubactions:S7637 \
    "-Dsonar.issue.ignore.multicriteria.e1.resourceKey=.github/workflows/**"

PROJECT_KEY=$(grep '^projectKey=' target/sonar/report-task.txt | cut -d= -f2-)
CE_TASK_ID=$(grep '^ceTaskId=' target/sonar/report-task.txt | cut -d= -f2-)

for i in $(seq 1 60); do
    CE_STATUS=$(api "${SONAR_URL}/api/ce/task?id=${CE_TASK_ID}" | json "d['task']['status']")
    case "${CE_STATUS}" in
        SUCCESS) break ;;
        FAILED|CANCELED) echo "ERROR: analysis task ${CE_STATUS}." >&2; exit 1 ;;
        *) ;; # PENDING or IN_PROGRESS: keep waiting
    esac
    if [[ "$i" -eq 60 ]]; then
        echo "ERROR: analysis did not finish within 2 minutes." >&2
        exit 1
    fi
    sleep 2
done

echo ""
echo "Quality gate:"
GATE_STATUS=$(api "${SONAR_URL}/api/qualitygates/project_status?projectKey=${PROJECT_KEY}" | python3 -c "
import json, sys
status = json.load(sys.stdin)['projectStatus']
for c in status.get('conditions', []):
    print(f\"  {c['metricKey']}: {c.get('actualValue', 'N/A')} (threshold {c.get('errorThreshold', 'N/A')}) - {c['status']}\", file=sys.stderr)
print(status['status'])
")
echo "  Status: ${GATE_STATUS}"

MERGE_BASE=$(git merge-base "${BASE_BRANCH}" HEAD)
echo ""
echo "Open issues touching lines changed since ${BASE_BRANCH}:"
CHANGED_ISSUES=$(issue_pages | MERGE_BASE="${MERGE_BASE}" python3 -c "
import json, os, re, subprocess, sys

diff = subprocess.run(['git', 'diff', '-U0', os.environ['MERGE_BASE']], capture_output=True, text=True, check=True)
changed, current = {}, None
for line in diff.stdout.splitlines():
    if line.startswith('+++ '):
        current = line[6:] if line.startswith('+++ b/') else None
    elif line.startswith('@@') and current:
        start, count = re.search(r'\+(\d+)(?:,(\d+))?', line).groups()
        changed.setdefault(current, set()).update(range(int(start), int(start) + int(count or 1)))
untracked = subprocess.run(['git', 'ls-files', '--others', '--exclude-standard'], capture_output=True, text=True, check=True)
for path in untracked.stdout.split():
    changed[path] = None

count = 0
for issue in (issue for page in sys.stdin for issue in json.loads(page)['issues']):
    path = issue['component'].split(':', 1)[-1]
    if path not in changed:
        continue
    lines, line = changed[path], issue.get('line')
    ranges = [issue] + [loc for flow in issue.get('flows', []) for loc in flow.get('locations', [])
                        if loc.get('component') == issue['component']]
    locations = {number for loc in ranges if 'textRange' in loc
                 for number in range(loc['textRange']['startLine'], loc['textRange']['endLine'] + 1)}
    if lines is None or line is None or locations & lines:
        count += 1
        print(f\"  {issue['rule']} {path}:{line or '-'} - {issue['message']}\", file=sys.stderr)
print(count)
")
echo "  Total: ${CHANGED_ISSUES}"

if [[ "${GATE_STATUS}" != "OK" || "${CHANGED_ISSUES}" != "0" ]]; then
    echo ""
    echo "FAILED: quality gate ${GATE_STATUS}, ${CHANGED_ISSUES} open issue(s) touching changed lines."
    exit 1
fi
echo ""
echo "PASSED"
