#!/bin/bash

# Exit immediately if a command fails
set -e

echo "Starting reset process for MTL Demo stacks..."

# --- Configuration Variables ---
SHARED_DIR="/opt/mtl/shared"
SHARED_PROJ="mtl-shared"

DIR_DEMO="/opt/mtl/mtl-demo.mindalyze.com"
PROJ_DEMO="mtl-demo-mindalyze-com"
DATA_DEMO="/data/mtl-demo.mindalyze.com"

DIR_LARGE="/opt/mtl/mtl-demo-large.mindalyze.com"
PROJ_LARGE="mtl-demo-large-mindalyze-com"
DATA_LARGE="/data/mtl-demo-large.mindalyze.com"

# --- 1. Stop both stacks ---
echo "----------------------------------------"
echo "1/4: Stopping Docker Compose stacks..."
echo "----------------------------------------"
# '|| true' prevents the script from failing if the containers are already down
cd "$DIR_DEMO" && docker compose -p "$PROJ_DEMO" down || true
cd "$DIR_LARGE" && docker compose -p "$PROJ_LARGE" down || true

# --- 2. Wipe data ---
echo "----------------------------------------"
echo "2/4: Wiping PostGIS, GPX, media, and logs..."
echo "----------------------------------------"
rm -rf "${DATA_DEMO}"/{PostGIS,GPX,media,logs}/*
rm -rf "${DATA_LARGE}"/{PostGIS,GPX,media,logs}/*
echo "Data wiped successfully."

# --- 3. Refresh shared services ---
echo "----------------------------------------"
echo "3/4: Pulling and starting shared services..."
echo "----------------------------------------"
cd "$SHARED_DIR"
docker compose -p "$SHARED_PROJ" pull
docker compose -p "$SHARED_PROJ" up -d

# --- 4. Start both stacks ---
echo "----------------------------------------"
echo "4/4: Starting Docker Compose stacks in detached mode..."
echo "----------------------------------------"
cd "$DIR_DEMO" && docker compose -p "$PROJ_DEMO" up -d
cd "$DIR_LARGE" && docker compose -p "$PROJ_LARGE" up -d

echo "----------------------------------------"
echo "✅ Process complete! Both stacks have been wiped and restarted."
