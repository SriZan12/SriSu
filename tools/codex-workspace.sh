#!/usr/bin/env bash
set -euo pipefail
srisu_frontend="$(cd "$(dirname "$0")/.." && pwd)"
srisu_backend="${SRISU_BACKEND_DIR:-$srisu_frontend/../SriSu-backend}"
python3 "$srisu_frontend/tools/workspace.py" status
exec codex --cd "$srisu_frontend" --add-dir "$srisu_backend" "$@"
