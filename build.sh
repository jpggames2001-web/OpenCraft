#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
if [[ -f /workspace/.opencraft-java/activate.sh ]]; then
  source /workspace/.opencraft-java/activate.sh
fi
if ! command -v javac >/dev/null || ! command -v mvn >/dev/null; then
  printf '%s\n' 'Install JDK 21 and Maven 3.9+, then run ./build.sh again.' >&2
  exit 1
fi
mvn --batch-mode --no-transfer-progress --strict-checksums verify "$@"
