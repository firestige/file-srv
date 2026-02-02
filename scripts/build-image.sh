#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${1:-icc-file-srv:latest}"

"${ROOT_DIR}/scripts/assemble-dist.sh"

docker build -t "${IMAGE_NAME}" "${ROOT_DIR}"

echo "Built image: ${IMAGE_NAME}"
