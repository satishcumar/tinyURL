#!/usr/bin/env bash
set -euo pipefail

api_base="${1:-http://localhost:8080}"

post_json() {
  local endpoint="$1"
  local payload="$2"
  curl --fail --silent --show-error -X POST "${api_base}${endpoint}" \
    -H 'Content-Type: application/json' -d "${payload}"
}

run_scenario() {
  local label="$1"
  local requirement="$2"
  local schema_approval="$3"

  local created execution_id approved completed
  created="$(post_json '/api/v1/workflows' "{\"requirement\":\"${requirement}\"}")"
  execution_id="$(py -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"${created}")"
  echo "${label}: ${execution_id} -> AWAITING_PLAN_APPROVAL"

  approved="$(post_json "/api/v1/workflows/${execution_id}/plan-approval" \
    "{\"approvedBy\":\"assessment-reviewer\",\"rationale\":\"Intent, risks, and DAG reviewed\"}")"

  if [[ "${schema_approval}" == "true" ]]; then
    py -c 'import json,sys; assert json.load(sys.stdin)["status"] == "AWAITING_SCHEMA_APPROVAL"' <<<"${approved}"
    post_json "/api/v1/workflows/${execution_id}/schema-approval" \
      '{"approvedBy":"database-owner","rationale":"Recovery and preservation controls reviewed"}' >/dev/null
  fi

  completed="$(post_json "/api/v1/workflows/${execution_id}/execution" '{}')"
  py -c 'import json,sys; value=json.load(sys.stdin); assert value["status"] == "COMPLETED"; assert value["metrics"]["successRate"] == 1.0' <<<"${completed}"
  echo "${label}: COMPLETED; artifacts build/orchestration-runs/${execution_id}"
}

run_scenario "GREENFIELD" "Add URL expiration and lifecycle management" "false"
run_scenario "BROWNFIELD" "Replace create-drop with Flyway migrations while preserving existing data" "true"
run_scenario "AMBIGUOUS" "Provide richer analytics" "false"
