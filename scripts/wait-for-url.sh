#!/usr/bin/env bash
set -euo pipefail

URL="${1:?Usage: wait-for-url.sh <url> [timeout-seconds]}"
TIMEOUT_SECONDS="${2:-60}"
DEADLINE=$((SECONDS + TIMEOUT_SECONDS))

until curl -fsS "${URL}" >/dev/null; do
  if (( SECONDS >= DEADLINE )); then
    echo "Timed out waiting for ${URL}" >&2
    exit 1
  fi
  sleep 2
done

echo "${URL} is ready"
