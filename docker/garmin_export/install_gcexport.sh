#!/bin/bash
# install_gcexport.sh <version>
#
# Installs a specific version of garmin-connect-export into a versioned virtual environment.
# Idempotent: if venv_gcexport_<version>/ already exists, exits immediately (fast path).
#
# Used both at Docker build time (Dockerfile RUN) and at runtime (admin on-demand install).
#
# Arguments:
#   $1 = version tag, e.g. v4.6.2

set -euo pipefail

VERSION="${1:-}"

# --- Validate version format ---
if [[ -z "$VERSION" ]]; then
    echo "ERROR: version argument is required. Usage: install_gcexport.sh <version>  e.g. v4.6.2"
    exit 1
fi

if ! [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo "ERROR: invalid version format '${VERSION}'. Expected format: v<major>.<minor>[.<patch>]  e.g. v4.6.2"
    exit 1
fi

# --- Resolve base directory (script's own location) ---
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

VENV_DIR="${BASE_DIR}/venv_gcexport_${VERSION}"
SRC_DIR="${BASE_DIR}/gcexport_src_${VERSION}"

echo "=== gcexport install: version=${VERSION} ==="
echo "    base dir : ${BASE_DIR}"
echo "    venv dir : ${VENV_DIR}"
echo "    src  dir : ${SRC_DIR}"

# --- Idempotency check: venv directory presence is the sentinel ---
if [[ -d "${VENV_DIR}" ]]; then
    echo "SKIP: venv_gcexport_${VERSION} already present — no install needed."
    exit 0
fi

echo "INFO: venv_gcexport_${VERSION} not found — proceeding with install."

# --- Ensure python3-venv is available ---
apt-get update -qq
apt-get install -y -qq python3 python3-pip python3-venv

# --- Download & extract source ---
echo "Downloading garmin-connect-export ${VERSION}..."
TARBALL="${BASE_DIR}/gcexport_${VERSION}.tar.gz"
curl -fsSL "https://github.com/pe-st/garmin-connect-export/archive/refs/tags/${VERSION}.tar.gz" \
    --output "${TARBALL}"

echo "Extracting to ${SRC_DIR}..."
mkdir -p "${SRC_DIR}"
tar -xzf "${TARBALL}" -C "${SRC_DIR}" --strip-components=1
rm -f "${TARBALL}"

# --- Create versioned virtual environment ---
echo "Creating virtual environment at ${VENV_DIR}..."
python3 -m venv "${VENV_DIR}"

# --- Install dependencies ---
echo "Installing dependencies from ${SRC_DIR}/requirements.txt..."
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip -q
pip install -r "${SRC_DIR}/requirements.txt"
deactivate

# --- Finalize ---
chmod -R 755 "${VENV_DIR}"
chmod -R 755 "${SRC_DIR}"

echo "INSTALLED: gcexport ${VERSION} → ${VENV_DIR}"
echo "=== gcexport install complete ==="
