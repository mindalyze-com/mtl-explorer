#!/bin/sh
# ─────────────────────────────────────────────────────────────────────
# download_map.sh — Download the PMTiles planet file (resume-capable)
#
# Expects:
#   $1  — Target file path          (e.g. /data/planet.pmtiles)
#   $2  — Download URL              (e.g. https://build.protomaps.com/20260327.pmtiles)
#         or "latest" to auto-resolve today's / yesterday's build
#
# Behaviour:
#   • Skips entirely if the target file's .done sentinel exists.
#   • Resumes via wget --continue if the file exists but the sentinel is missing.
#   • Uses wget --continue so a killed container resumes where it left off.
#   • "latest" mode tries today, yesterday, then day-before-yesterday.
# ─────────────────────────────────────────────────────────────────────
set -e

. /app/scripts/common.sh

TARGET="$1"
URL="$2"
DONE_FILE="${TARGET}.done"
URL_FILE="${TARGET}.url"

# ─── Already fully downloaded? ───────────────────────────────────────
if [ -f "$DONE_FILE" ]; then
  log "Planet file already complete: $TARGET ($(du -h "$TARGET" | cut -f1)) — skipping download"
  exit 0
fi

# ─── Partial download present? ───────────────────────────────────────
if [ -f "$TARGET" ]; then
  log "Planet file present but sentinel missing — resuming incomplete download ($(du -h "$TARGET" | cut -f1) so far)"
fi

# ─── Resolve "latest" to a concrete URL ──────────────────────────────
if [ "$URL" = "latest" ]; then
  if [ -f "$URL_FILE" ]; then
    URL=$(cat "$URL_FILE")
    log "Resuming with previously resolved 'latest' URL: $URL"
  else
    log "MAP_DOWNLOAD_URL=latest — resolving most recent daily build ..."
    RESOLVED=""
    for DAYS_AGO in 0 1 2 3; do
      CANDIDATE_DATE=$(date -u -d "-${DAYS_AGO} days" '+%Y%m%d' 2>/dev/null \
                    || date -v-${DAYS_AGO}d '+%Y%m%d')  # GNU date || BSD date
      CANDIDATE_URL="${PROTOMAPS_BUILDS_BASE_URL:-https://build.protomaps.com}/${CANDIDATE_DATE}.pmtiles"
      log "  Checking $CANDIDATE_URL ..."
      if wget --spider --quiet "$CANDIDATE_URL" 2>/dev/null; then
        RESOLVED="$CANDIDATE_URL"
        log "  Found: $RESOLVED"
        break
      fi
    done
    [ -z "$RESOLVED" ] && die "Could not resolve 'latest' — no daily build found for the last 4 days."
    URL="$RESOLVED"
    echo "$URL" > "$URL_FILE"
    log "Saved resolved URL to $URL_FILE"
  fi
fi

# ─── Validate URL ────────────────────────────────────────────────────
[ -z "$URL" ] && die "No download URL provided and $TARGET does not exist."

# ─── Download with resume support ────────────────────────────────────
log "Downloading planet file to $TARGET"
log "  URL: $URL"
log "  This is a large file (~120 GB). The download will resume if interrupted."

wget \
  --user-agent="mytraillog-map-server/1.0" \
  --continue \
  --show-progress \
  --progress=bar:force:noscroll \
  --timeout=60 \
  --waitretry=10 \
  --tries=0 \
  -O "$TARGET" \
  "$URL" \
|| die "Download failed. Re-run the container to resume from where it stopped."

touch "$DONE_FILE"
log "Download complete: $TARGET ($(du -h "$TARGET" | cut -f1))"
