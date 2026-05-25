#!/bin/bash
set -euo pipefail

echo "Starting reset process for MTL Demo Beta stack..."

SHARED_DIR="/opt/mtl/shared"
SHARED_PROJ="mtl-shared"

DIR_BETA="/opt/mtl/mtl-demo-beta.mindalyze.com"
PROJ_BETA="mtl-demo-beta-mindalyze-com"
DATA_BETA="/data/mtl-demo-beta.mindalyze.com"

if [[ ! -d "$DIR_BETA" ]]; then
  echo "Missing install directory: $DIR_BETA" >&2
  exit 1
fi

if [[ ! -d "$DATA_BETA" ]]; then
  echo "Missing data directory: $DATA_BETA" >&2
  exit 1
fi

echo "----------------------------------------"
echo "1/5: Stopping Docker Compose stack..."
echo "----------------------------------------"
cd "$DIR_BETA"
docker compose -p "$PROJ_BETA" down --remove-orphans || true

echo "----------------------------------------"
echo "2/5: Wiping PostGIS, GPX, media, and logs..."
echo "----------------------------------------"
for subdir in PostGIS GPX media logs; do
  target="${DATA_BETA}/${subdir}"
  mkdir -p "$target"
  find "$target" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
done
echo "Data wiped successfully."

echo "----------------------------------------"
echo "3/5: Pulling and starting shared services..."
echo "----------------------------------------"
cd "$SHARED_DIR"
docker compose -p "$SHARED_PROJ" pull
docker compose -p "$SHARED_PROJ" up -d

echo "----------------------------------------"
echo "4/5: Pulling current beta images..."
echo "----------------------------------------"
cd "$DIR_BETA"
docker compose -p "$PROJ_BETA" pull

echo "----------------------------------------"
echo "5/5: Starting Docker Compose stack in detached mode..."
echo "----------------------------------------"
docker compose -p "$PROJ_BETA" up -d

echo "----------------------------------------"
echo "Process complete! Beta stack has been wiped and restarted."
