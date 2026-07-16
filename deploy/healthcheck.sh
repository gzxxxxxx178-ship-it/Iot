#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
TIMEOUT_SECONDS="${HEALTHCHECK_TIMEOUT_SECONDS:-60}"
DEADLINE=$((SECONDS + TIMEOUT_SECONDS))

while (( SECONDS < DEADLINE )); do
    BODY="$(curl --silent --show-error --max-time 5 "$BASE_URL/actuator/health" 2>/dev/null || true)"
    if [[ "$BODY" == *'"status":"UP"'* ]]; then
        printf 'healthcheck passed: %s/actuator/health\n' "$BASE_URL"
        exit 0
    fi
    sleep 2
done

printf 'healthcheck failed: %s/actuator/health\n' "$BASE_URL" >&2
exit 1
