#!/bin/bash

readonly LIVE_WATCH_READY_TIMEOUT_SECONDS=1800
readonly LIVE_WATCH_READY_POLL_SECONDS=1
readonly DEMO_PHOTO_GENERATION_MARKER_NAME=".demo-photo-generation-in-progress"

wait_for_live_watcher() {
  local watch_directory="$1"
  local inode
  local inode_hex
  local waited_seconds=0

  inode=$(stat -c %i "$watch_directory") || return 1
  inode_hex=$(printf '%x' "$inode")

  while (( waited_seconds < LIVE_WATCH_READY_TIMEOUT_SECONDS )); do
    if grep -qs "^inotify .* ino:${inode_hex} " /proc/1/fdinfo/*; then
      return 0
    fi
    sleep "$LIVE_WATCH_READY_POLL_SECONDS"
    ((waited_seconds += LIVE_WATCH_READY_POLL_SECONDS))
  done
  return 1
}

generate_demo_photos_in_background() {
  local gpx_directory="$1"
  local media_directory="$2"
  local photo_count="$3"
  local eligible_track_count="$4"
  local generation_marker="$media_directory/$DEMO_PHOTO_GENERATION_MARKER_NAME"

  touch "$generation_marker"

  echo "Waiting for the GPS and media live watchers before generating demo photos …"
  if ! wait_for_live_watcher "$gpx_directory"; then
    echo "Demo photo generation skipped: GPS live watcher did not start within ${LIVE_WATCH_READY_TIMEOUT_SECONDS}s."
    rm -f "$generation_marker"
    return 1
  fi
  if ! wait_for_live_watcher "$media_directory"; then
    echo "Demo photo generation skipped: media live watcher did not start within ${LIVE_WATCH_READY_TIMEOUT_SECONDS}s."
    rm -f "$generation_marker"
    return 1
  fi

  echo "GPS and media live watchers are ready. Generating up to $photo_count demo photos in the background …"
  if python3 -u /app/demo/generate_demo_photos.py \
      "$gpx_directory" \
      "$media_directory" \
      "$photo_count" \
      "$eligible_track_count"; then
    echo "Background demo photo generation completed."
  else
    echo "Background demo photo generation failed. It will resume on the next startup."
  fi
  rm -f "$generation_marker"
}

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
  DEMO_TARGET_TRACK_COUNT="${DEMO_TARGET_TRACK_COUNT:-0}"

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

  # 2. Inject Spring profile 'demo' into the Java command
  set -- "$@" "--spring.profiles.active=demo"
  echo "Spring profile 'demo' activated."

  # 3. Start photo generation after the initial GPS import and both live watchers
  #    are ready. JPEGs are published atomically, so the running app only discovers
  #    complete files without competing with the initial GPX import for memory.
  generate_demo_photos_in_background \
    "$DEMO_GPX_DIR" \
    /app/media \
    "$DEMO_PHOTO_COUNT" \
    "$DEMO_TARGET_TRACK_COUNT" &

  echo "========================================"
fi

# Now start the Java Spring Boot application (CMD provided is used here via exec "$@")
echo "Starting Java application..."
exec "$@"
