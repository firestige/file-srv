#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

cd "${ROOT_DIR}"

# Build only what we need for the runtime fat jar
mvn -pl file-srv-bootstrap -am -DskipTests package

# Prepare dist layout
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"/plugins "${DIST_DIR}"/adapters "${DIST_DIR}"/spi "${DIST_DIR}"/aspect

# Copy bootstrap fat jar
cp "${ROOT_DIR}/file-srv-bootstrap/target/app.jar" "${DIST_DIR}/app.jar"

copy_jars() {
  local src_dir="$1"
  local dest_dir="$2"
  if [[ -d "${src_dir}" ]]; then
    find "${src_dir}" -type f -name "*.jar" \
      ! -name "*-sources.jar" \
      ! -name "*-javadoc.jar" \
      ! -name "*-tests.jar" \
      ! -name "*test*.jar" \
      ! -name "*-plain.jar" \
      -print0 | while IFS= read -r -d '' jar; do
        cp "$jar" "${dest_dir}/"
      done
  fi
}

# Copy plugin jars into external extension directories
copy_jars "${ROOT_DIR}/file-srv-plugins" "${DIST_DIR}/plugins"
copy_jars "${ROOT_DIR}/file-srv-adapters" "${DIST_DIR}/adapters"
copy_jars "${ROOT_DIR}/file-srv-spi" "${DIST_DIR}/spi"
copy_jars "${ROOT_DIR}/file-srv-aspect" "${DIST_DIR}/aspect"

echo "Dist ready at ${DIST_DIR}"
