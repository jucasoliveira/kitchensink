#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" == "--clean" ]]; then
  docker compose down --volumes
else
  docker compose down
fi