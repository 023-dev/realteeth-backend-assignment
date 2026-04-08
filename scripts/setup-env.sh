#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=".env"
ENV_EXAMPLE=".env.example"
ISSUE_KEY_URL="${MOCK_WORKER_ISSUE_KEY_URL:-https://dev.realteeth.ai/mock/auth/issue-key}"

candidate_name="${1:-${MOCK_WORKER_CANDIDATE_NAME:-}}"
email="${2:-${MOCK_WORKER_EMAIL:-}}"

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required"
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 is required"
  exit 1
fi

if [ ! -f "$ENV_EXAMPLE" ]; then
  echo "ERROR: $ENV_EXAMPLE not found"
  exit 1
fi

if [ -z "$candidate_name" ] && [ -t 0 ]; then
  read -r -p "candidateName: " candidate_name
fi

if [ -z "$email" ] && [ -t 0 ]; then
  read -r -p "email: " email
fi

if [ -z "$candidate_name" ] || [ -z "$email" ]; then
  echo "Usage: $0 <candidateName> <email>"
  echo "Or set MOCK_WORKER_CANDIDATE_NAME / MOCK_WORKER_EMAIL"
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo ".env created from .env.example"
else
  echo ".env already exists, updating MOCK_WORKER_API_KEY only"
fi

payload="$(python3 -c 'import json,sys; print(json.dumps({"candidateName": sys.argv[1], "email": sys.argv[2]}))' "$candidate_name" "$email")"

echo "Issuing Mock Worker API Key..."
response="$(curl -sS -X POST "$ISSUE_KEY_URL" \
  -H 'Content-Type: application/json' \
  -d "$payload")"

api_key="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("apiKey",""))' 2>/dev/null || true)"

if [ -z "$api_key" ]; then
  echo "ERROR: Failed to issue API Key"
  echo "Response: $response"
  exit 1
fi

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

awk -v api_key="$api_key" '
BEGIN { updated = 0 }
/^MOCK_WORKER_API_KEY=/ {
  print "MOCK_WORKER_API_KEY=" api_key
  updated = 1
  next
}
{ print }
END {
  if (updated == 0) {
    print "MOCK_WORKER_API_KEY=" api_key
  }
}
' "$ENV_FILE" > "$tmp_file"

mv "$tmp_file" "$ENV_FILE"

echo "MOCK_WORKER_API_KEY written to .env"
echo ""
echo "Next:"
echo "  docker compose up --build"
