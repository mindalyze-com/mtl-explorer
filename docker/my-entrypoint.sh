#!/bin/bash

sleep 15

# ============================================================
# DEMO MODE — activated when DEMO_MODE env var is set (any value)
# ============================================================
if [ -n "${DEMO_MODE}" ]; then
  echo "========================================"
  echo "  DEMO MODE ACTIVATED"
  echo "========================================"

  DEMO_GPX_DIR="/app/gpx/demo_gpx_porto_taxi_dataset"
  DEMO_ZIP="/app/demo/porto_taxi_service_gpx_extract.zip"
  DEMO_PHOTO_COUNT="${DEMO_PHOTO_COUNT:-200}"

  # 1. Unzip ALL demo GPX tracks (idempotent — skips if folder already populated)
  #    Track count trimming happens later in Java (DemoTrackExclusionService) via
  #    mtl.demo-target-track-count so the final count is exact even after bad-track exclusion.
  if [ -d "$DEMO_GPX_DIR" ] && [ "$(ls -A "$DEMO_GPX_DIR"/*.gpx 2>/dev/null | head -1)" ]; then
    echo "Demo GPX tracks already present ($(ls "$DEMO_GPX_DIR"/*.gpx | wc -l | tr -d ' ') files) — skipping unzip."
  else
    echo "Extracting ALL demo GPX tracks to $DEMO_GPX_DIR …"
    mkdir -p "$DEMO_GPX_DIR"
    unzip -o -q "$DEMO_ZIP" -d "$DEMO_GPX_DIR"
    echo "Extracted $(ls "$DEMO_GPX_DIR"/*.gpx 2>/dev/null | wc -l | tr -d ' ') GPX files."
  fi

  # Copy citation file into the media volume so users browsing mounted folders can see it
  mkdir -p /app/media/demo-photos
  cp /app/demo/DATASOURCE.md /app/media/demo-photos/DATASOURCE.md

  # 2. Generate demo photos (resumable — the script checks existing count)
  echo "Generating up to $DEMO_PHOTO_COUNT demo photos …"
  python3 /app/demo/generate_demo_photos.py "$DEMO_GPX_DIR" /app/media "$DEMO_PHOTO_COUNT"

  # 3. Inject Spring profile 'demo' into the Java command
  set -- "$@" "--spring.profiles.active=demo"
  echo "Spring profile 'demo' activated."
  echo "========================================"
fi

# Now start the Java Spring Boot application (CMD provided is used here via exec "$@")
echo "Starting Java application..."
exec "$@"
