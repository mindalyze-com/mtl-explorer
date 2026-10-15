#!/bin/bash

readonly GENERATOR_SHUTDOWN_GRACE_SECONDS=5
readonly DEMO_PHOTO_HARD_TIMEOUT_SECONDS=600
startup_child_pid=""

log_startup() {
  local level="$1"
  shift
  printf '%s %-5s [entrypoint] DemoStartup : %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%S.%3NZ')" "$level" "$*"
}

stop_startup() {
  local shutdown_status="$1"
  local shutdown_signal="$2"
  log_startup INFO "Received $shutdown_signal; stopping startup child and render workers."
  trap '' TERM INT
  if [[ -n "$startup_child_pid" ]]; then
    kill -"$shutdown_signal" -- "-$startup_child_pid" 2>/dev/null || kill -"$shutdown_signal" "$startup_child_pid" 2>/dev/null || true
    # Bound shutdown even if a native codec cannot terminate.
    sleep "$GENERATOR_SHUTDOWN_GRACE_SECONDS" &
    local watchdog_pid=$!
    local completed_pid=""
    wait -n -p completed_pid "$startup_child_pid" "$watchdog_pid" 2>/dev/null || true
    if [[ "$completed_pid" == "$watchdog_pid" ]]; then
      kill -KILL -- "-$startup_child_pid" 2>/dev/null || kill -KILL "$startup_child_pid" 2>/dev/null || true
      wait "$startup_child_pid" 2>/dev/null || true
    else
      kill -KILL "$watchdog_pid" 2>/dev/null || true
      wait "$watchdog_pid" 2>/dev/null || true
    fi
  fi
  log_startup INFO "Startup stopped by shutdown signal; Java will not start."
  exit "$shutdown_status"
}

wait_startup_child() {
  local result=0
  wait "$startup_child_pid" || result=$?
  startup_child_pid=""
  return "$result"
}

main() {
  trap 'stop_startup 143 TERM' TERM
  trap 'stop_startup 130 INT' INT
  local started=$SECONDS
  log_startup INFO "Container startup: demo-mode=${DEMO_MODE:-disabled}."
  if [[ -n "${DEMO_MODE:-}" ]]; then
    local gpx_directory="/app/gpx/demo_gpx_porto_taxi_dataset"
    local demo_zip="/app/demo/porto_taxi_service_gpx_extract.zip"
    log_startup INFO "Demo preparation invoked; it may take a few minutes before the application starts. GPS indexing continues after startup."
    # Keep all tracks; Java applies the visible track target after import.
    if compgen -G "$gpx_directory/*.gpx" >/dev/null && [[ ! -f "$gpx_directory/.demo-track-generation-in-progress" ]]; then
      log_startup INFO "Demo GPX files already present; skipping extraction."
    else
      mkdir -p "$gpx_directory"
      local extraction_started=$SECONDS
      log_startup INFO "Creating all demo GPX files with offline terrain elevation from $demo_zip to $gpx_directory."
      PYTHONPATH=/app/demo setsid python3 -u -m elevation.create_demo_tracks "$demo_zip" "$gpx_directory" &
      startup_child_pid=$!
      if ! wait_startup_child; then
        log_startup WARN "Demo GPX creation failed; completed tracks are retained for resume; continuing startup."
      else
        local extracted_count
        extracted_count=$(find "$gpx_directory" -type f -name '*.gpx' | wc -l)
        log_startup INFO "Demo GPX creation completed: files=$extracted_count, duration=$((SECONDS - extraction_started))s."
      fi
    fi
    mkdir -p /app/media/demo-photos
    cp /app/demo/DATASOURCE.md /app/media/demo-photos/DATASOURCE.md
    # Clean the obsolete coordination file from volumes created by older images.
    rm -f /app/media/.demo-photo-generation-in-progress
    local generation_started=$SECONDS
    log_startup INFO "Starting demo photo preparation: count=${DEMO_PHOTO_COUNT:-200}, workers=${DEMO_PHOTO_WORKERS:-4}, hard timeout=${DEMO_PHOTO_HARD_TIMEOUT_SECONDS}s."
    setsid timeout --signal=TERM --kill-after="${GENERATOR_SHUTDOWN_GRACE_SECONDS}s" \
      "${DEMO_PHOTO_HARD_TIMEOUT_SECONDS}s" python3 -u /app/demo/generate_demo_photos.py \
      "$gpx_directory" /app/media "${DEMO_PHOTO_COUNT:-200}" "${DEMO_TARGET_TRACK_COUNT:-0}" &
    startup_child_pid=$!
    local generation_result=0
    wait_startup_child || generation_result=$?
    if ((generation_result != 0)); then
      log_startup WARN "Demo photo generation failed or timed out; Java will start. Photos resume on next startup. Exit code=$generation_result, duration=$((SECONDS - generation_started))s."
    else
      log_startup INFO "Demo photo preparation completed: duration=$((SECONDS - generation_started))s."
    fi
    set -- "$@" "--spring.profiles.active=demo"
    log_startup INFO "Spring demo profile activated."
  fi
  log_startup INFO "Starting Java application; preparation duration=$((SECONDS - started))s."
  exec "$@"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
