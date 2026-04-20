#!/bin/bash

# Wrapper script to call garmin_fit_to_gpx_export.py for a specific activity ID
# Arguments:
# $1 = Garmin username/email
# $2 = Garmin password
# $3 = Activity ID (e.g., 20568140940)
# $4 = Save directory for the GPX and FIT files

USERNAME="$1"
PASSWORD="$2"
ACTIVITY_ID="$3"
SAVE_DIR="$4"
PROFILE="${5:-default}"

echo "Starting garmin_fit_to_gpx_export.py for activity ID: $ACTIVITY_ID"
echo "Save directory: $SAVE_DIR"
echo "INFO: fit-export profile: ${PROFILE}"

BASE_DIR="/app/garmin_fit_export"
VENV_DIR="${BASE_DIR}/venv_fit_${PROFILE}"

# Verify venv exists (install_fit_export.sh must have been called first)
if [[ ! -d "${VENV_DIR}" ]]; then
    echo "ERROR: venv_fit_${PROFILE} not found at ${VENV_DIR}. Run install_fit_export.sh ${PROFILE} first."
    exit 1
fi

echo "INFO: using venv: ${VENV_DIR}"

# Set the password as environment variable (more secure than command line)
export GARMIN_PWD="$PASSWORD"

# Change to the directory containing the Python script
cd "$(dirname "$0")"

# Use the profile-versioned virtual environment Python
PYTHON_BIN="${VENV_DIR}/bin/python3"

# Call the Python script
$PYTHON_BIN garmin_fit_to_gpx_export.py \
  --email "$USERNAME" \
  --activity "$ACTIVITY_ID" \
  --save-dir "$SAVE_DIR"

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
  echo "Successfully exported activity $ACTIVITY_ID using garmin_fit_to_gpx_export.py"
else
  echo "Failed to export activity $ACTIVITY_ID using garmin_fit_to_gpx_export.py (exit code: $EXIT_CODE)"
fi

exit $EXIT_CODE
