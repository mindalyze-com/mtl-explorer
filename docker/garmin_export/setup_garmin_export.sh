#!/bin/bash

# Setup script for garmin-connect-export (gcexport)
# Creates a virtual environment and installs dependencies

echo "=== Setting up garmin-connect-export tool ==="

# Install Python3 and venv (should already be available from base Dockerfile)
apt-get update
apt-get install -y python3 python3-pip python3-venv

# Show Python version
echo "Python version:"
python3 --version

# Change working directory to the directory of the script
cd "$(dirname "$0")"

chmod -R 777 garmin-connect-export 2>/dev/null || true

GCEXPORT_VERSION="v4.6.2"

# download a specific version
echo "Downloading garmin-connect-export ${GCEXPORT_VERSION}..."
curl -L "https://github.com/pe-st/garmin-connect-export/archive/refs/tags/${GCEXPORT_VERSION}.tar.gz" --output gcexport.tar.gz
tar -xvzf gcexport.tar.gz

# normalize the extracted folder name to garmin-connect-export
mv garmin-connect-export* garmin-connect-export

# once again set permissions very open
chmod -R 777 garmin-connect-export
chmod -R 777 /app/garmin_export/*
chmod 777 /app/garmin_export/run_export.sh

# Create virtual environment
echo "Creating virtual environment at venv_gcexport/"
python3 -m venv venv_gcexport

# Activate virtual environment and install dependencies
echo "Installing dependencies from requirements.txt"
cd garmin-connect-export
source ../venv_gcexport/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
deactivate
cd ..

# Set permissions on venv
chmod -R 755 venv_gcexport

echo "=== garmin-connect-export setup completed ==="
echo "Virtual environment: /app/garmin_export/venv_gcexport"
echo "Python binary: /app/garmin_export/venv_gcexport/bin/python3"
echo "Python version in venv:"
/app/garmin_export/venv_gcexport/bin/python3 --version
echo "gcexport.py location: /app/garmin_export/garmin-connect-export/gcexport.py"