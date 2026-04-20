#!/bin/bash
# install_fit_export.sh <profile> "<pip packages>"
#
# Installs fit-export Python dependencies into a profile-named virtual environment.
# Idempotent: if venv_fit_<profile>/ already exists, exits immediately (fast path).
#
# Used both at Docker build time (Dockerfile RUN) and at runtime (admin on-demand install).
#
# Arguments:
#   $1 = profile name, e.g. "default" or "garth-0.4.46"  (alphanumeric, hyphens, underscores)
#   $2 = space-separated pip package specs, e.g. "garth fitparse gpxpy"
#         or with pins: "garth==0.4.46 fitparse==1.2.0 gpxpy==1.5.0"

set -euo pipefail

PROFILE="${1:-}"
PACKAGES="${2:-}"

# --- Validate profile name ---
if [[ -z "$PROFILE" ]]; then
    echo "ERROR: profile argument is required. Usage: install_fit_export.sh <profile> \"<pip packages>\""
    exit 1
fi

if ! [[ "$PROFILE" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "ERROR: invalid profile name '${PROFILE}'. Only alphanumerics, hyphens and underscores are allowed."
    exit 1
fi

# --- Validate pip package tokens (allowlist per token) ---
if [[ -z "$PACKAGES" ]]; then
    echo "ERROR: pip packages argument is required. Usage: install_fit_export.sh <profile> \"garth fitparse gpxpy\""
    exit 1
fi

for TOKEN in $PACKAGES; do
    if ! [[ "$TOKEN" =~ ^[a-zA-Z0-9_.=-]+$ ]]; then
        echo "ERROR: invalid pip package token '${TOKEN}'. Only alphanumerics, dots, hyphens, underscores and = are allowed."
        exit 1
    fi
done

# --- Resolve base directory ---
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

VENV_DIR="${BASE_DIR}/venv_fit_${PROFILE}"
PIP_SPEC_FILE="${VENV_DIR}/.pip_spec"

echo "=== fit-export install: profile=${PROFILE} ==="
echo "    base dir : ${BASE_DIR}"
echo "    venv dir : ${VENV_DIR}"
echo "    packages : ${PACKAGES}"

# --- Idempotency check ---
if [[ -d "${VENV_DIR}" ]]; then
    echo "SKIP: venv_fit_${PROFILE} already present — no install needed."
    if [[ -f "${PIP_SPEC_FILE}" ]]; then
        echo "INFO: installed packages: $(cat "${PIP_SPEC_FILE}")"
    fi
    exit 0
fi

echo "INFO: venv_fit_${PROFILE} not found — proceeding with install."

# --- Ensure python3-venv is available ---
apt-get update -qq
apt-get install -y -qq python3 python3-pip python3-venv

# --- Create versioned virtual environment ---
echo "Creating virtual environment at ${VENV_DIR}..."
python3 -m venv "${VENV_DIR}"

# --- Install packages ---
echo "Installing packages: ${PACKAGES}..."
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip -q
# shellcheck disable=SC2086
pip install $PACKAGES
deactivate

# --- Record pip spec for status display ---
echo "${PACKAGES}" > "${PIP_SPEC_FILE}"

# --- Ensure the main script is executable ---
chmod +x "${BASE_DIR}/garmin_fit_to_gpx_export.py" 2>/dev/null || true
chmod -R 755 "${VENV_DIR}"

echo "INSTALLED: fit-export profile=${PROFILE} packages=[${PACKAGES}] → ${VENV_DIR}"
echo "=== fit-export install complete ==="
