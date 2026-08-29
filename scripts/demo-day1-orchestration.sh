#!/usr/bin/env bash
set -euo pipefail

api_base="${1:-http://localhost:8080}"

requirement_response="$(curl --fail --silent --show-error \
  -X POST "${api_base}/api/v1/workflows" \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"Add URL expiration and lifecycle management"}')"

execution_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' \
  <<<"${requirement_response}")"

echo "Created workflow: ${execution_id}"
python3 -m json.tool <<<"${requirement_response}"

curl --fail --silent --show-error \
  -X POST "${api_base}/api/v1/workflows/${execution_id}/commands" \
  -H 'Content-Type: application/json' \
  -d '{"stageId":"inspect","command":"rg --files src/main src/test","exitCode":0,"startedAt":"2026-08-29T00:00:00Z","durationMillis":12,"outputDigest":"sha256:demo-evidence"}' \
  >/dev/null

approval_response="$(curl --fail --silent --show-error \
  -X POST "${api_base}/api/v1/workflows/${execution_id}/plan-approval" \
  -H 'Content-Type: application/json' \
  -d '{"approvedBy":"day1-reviewer","rationale":"Acceptance criteria, task graph, and risks reviewed"}')"

echo "Approved plan"
python3 -m json.tool <<<"${approval_response}"

execution_response="$(curl --fail --silent --show-error \
  -X POST "${api_base}/api/v1/workflows/${execution_id}/execution")"

echo "Executed workflow"
python3 -m json.tool <<<"${execution_response}"

echo "Reviewable artifacts"
curl --fail --silent --show-error \
  "${api_base}/api/v1/workflows/${execution_id}/artifacts" | python3 -m json.tool

echo "Artifact directory: build/orchestration-runs/${execution_id}"
