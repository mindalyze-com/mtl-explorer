#!/bin/bash
# run_export.sh <username> <password> <exclude-file> <gcexport-version>
#
# Arguments:
#   $1 = Garmin username/email
#   $2 = Garmin password
#   $3 = path to exclude activities JSON file
#   $4 = gcexport version to use, e.g. v4.6.2

set -euo pipefail

USERNAME="$1"
PASSWORD="$2"
EXCLUDE_FILE="$3"
VERSION="$4"

BASE_DIR="/app/garmin_export"
VENV_DIR="${BASE_DIR}/venv_gcexport_${VERSION}"
SRC_DIR="${BASE_DIR}/gcexport_src_${VERSION}"
export DOWNLOAD_DIR="${SRC_DIR}/GARMIN_CONNECT_EXPORTED_TRACKS"
export TARGET_DIR='/app/gpx/'

echo "INFO: gcexport run — version=${VERSION}"
echo "INFO: using venv  : ${VENV_DIR}"
echo "INFO: using src   : ${SRC_DIR}"

# Verify venv exists (install_gcexport.sh must have been called first)
if [[ ! -d "${VENV_DIR}" ]]; then
    echo "ERROR: venv_gcexport_${VERSION} not found at ${VENV_DIR}. Run install_gcexport.sh ${VERSION} first."
    exit 1
fi

# Use the versioned virtual environment Python
PYTHON_BIN="${VENV_DIR}/bin/python3"

# clean the download directory first
echo "clean the download directory $DOWNLOAD_DIR"
rm -rf "$DOWNLOAD_DIR"

echo "now invoke the garmin export tool to get the files."
"$PYTHON_BIN" "${SRC_DIR}/gcexport.py" -d "$DOWNLOAD_DIR" --username "$USERNAME" --password "$PASSWORD" --exclude "$EXCLUDE_FILE" --count all

echo "Copy the files from the download to the monitoring directory. Do not overwrite"
cp -rn "$DOWNLOAD_DIR" "$TARGET_DIR"
