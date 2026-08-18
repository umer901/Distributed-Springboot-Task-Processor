#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEY="${1:-demo-key-$(date +%s)}"

curl -i -X POST "${BASE_URL}/api/v1/tasks" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${KEY}" \
  -d '{"taskType":"CHECKSUM","payload":{"text":"hello"},"priority":0,"maxAttempts":3,"timeoutSeconds":30}'
