#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
if [[ -f /workspace/.opencraft-java/activate.sh ]]; then
  source /workspace/.opencraft-java/activate.sh
fi
if [[ ! -f target/opencraft-0.1.0.jar ]]; then
  ./build.sh
fi
exec java -jar target/opencraft-0.1.0.jar "$@"
