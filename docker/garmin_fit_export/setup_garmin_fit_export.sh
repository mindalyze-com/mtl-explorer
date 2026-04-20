#!/bin/bash

# Setup script for garmin_fit_to_gpx_export
# Creates a virtual environment and installs dependencies

echo "=== Setting up garmin_fit_export tool ==="

# Change working directory to the directory of the script
cd "$(dirname "$0")"

# Install Python3 and venv (should already be available from base Dockerfile)
apt-get update
apt-get install -y python3 python3-pip python3-venv

# Show Python version
echo "Python version:"
python3 --version

# Create virtual environment
echo "Creating virtual environment at venv_fit_export/"
python3 -m venv venv_fit_export

# Activate virtual environment and install dependencies
echo "Installing dependencies: garminconnect, fitparse, gpxpy"
source venv_fit_export/bin/activate
pip install --upgrade pip
pip install garminconnect fitparse gpxpy
deactivate

# Set permissions
chmod +x garmin_fit_to_gpx_export.py
chmod +x run_fit_export.sh
chmod -R 755 venv_fit_export

echo "=== garmin_fit_export setup completed ==="
echo "Virtual environment: /app/garmin_fit_export/venv_fit_export"
echo "Python binary: /app/garmin_fit_export/venv_fit_export/bin/python3"
echo "Python version in venv:"
/app/garmin_fit_export/venv_fit_export/bin/python3 --version
