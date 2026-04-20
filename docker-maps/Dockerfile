# ----- Self-downloading PMTiles map server -----
# Downloads the planet vector map file on first startup, then serves tiles.
# Two independent scopes: /data/demo/ (baked) and /data/prod/ (downloaded).
#
# Architecture:
#   nginx (port 8081, public) serves /data/ tree as static files.
#   Two scope directories: /data/demo/ and /data/prod/, each with versioned
#   files (YYYYMMDD.pmtiles) + symlinks (planet.pmtiles → YYYYMMDD.pmtiles).
#   The Java backend proxies scope-prefixed URLs: http://map-server:8081/{scope}/{filename}
#
#   GET /health                    — always 200 (Docker HEALTHCHECK)
#   GET /status                    — JSON progress (phase, pct, ready flag) for the Java backend
#   GET /{scope}/{file}.pmtiles    — static file serving with Range request support (symlinks resolved by nginx)
#
# Runtime environment variables:
#   MAP_DOWNLOAD_URL            - URL to download the .pmtiles planet file (required for prod extraction/downloading on first run)
#   PROTOMAPS_BUILDS_BASE_URL   - Override the daily-build base URL without rebuilding (default: https://build.protomaps.com)
#   MAP_AREA_BBOX               - Optional bbox "west,south,east,north" for area-only extract instead of full download
#   PORT                        - Public listen port for nginx (default: 8081)
#   LOWZOOM_MAXZOOM             - Max zoom level for the offline low-zoom extract (default: 6)
#
# Build:
#   # Prod image (no baked tiles):
#   docker build -t wauwau0977/mytraillog-maps:1.4 .
#
#   # Demo image (bakes Porto vector tiles at build time — ~250 MB larger):
#   docker build \
#     --build-arg BAKE_DEMO_AREA="-13.0,39.3,-4.5,42.6" \
#     --build-arg PROTOMAPS_URL=https://build.protomaps.com/20260404.pmtiles \
#     -t wauwau0977/mytraillog-maps:1.4 .

# ── Stage 1: base ────────────────────────────────────────────────────
FROM alpine:3.21 AS base

ARG PMTILES_VERSION=1.30.1

RUN apk add --no-cache \
    # network & download
    wget curl ca-certificates \
    # reverse proxy (serves /health, /status, and proxies tiles)
    nginx \
    # orchestrator runtime
    python3 \
    # editors
    vim nano \
    # shell utilities
    bash less grep sed gawk coreutils \
    # process & system inspection
    procps htop lsof \
    # file inspection
    file jq \
    # archive tools
    tar gzip

# Download the go-pmtiles binary
RUN ARCH=$(uname -m) && \
    case "$ARCH" in \
      x86_64)  TARBALL="go-pmtiles_${PMTILES_VERSION}_Linux_x86_64.tar.gz" ;; \
      aarch64) TARBALL="go-pmtiles_${PMTILES_VERSION}_Linux_arm64.tar.gz" ;; \
      *)       echo "Unsupported architecture: $ARCH" && exit 1 ;; \
    esac && \
    wget -q "https://github.com/protomaps/go-pmtiles/releases/download/v${PMTILES_VERSION}/${TARBALL}" -O /tmp/pmtiles.tar.gz && \
    tar -xzf /tmp/pmtiles.tar.gz -C /usr/local/bin pmtiles && \
    chmod +x /usr/local/bin/pmtiles && \
    rm /tmp/pmtiles.tar.gz

# The persistent data volume mount point
RUN mkdir -p /data/demo /data/prod /app/scripts
VOLUME /data

# nginx: replace default config with our reverse-proxy config
RUN rm -f /etc/nginx/http.d/default.conf
COPY nginx.conf /etc/nginx/http.d/map-server.conf

COPY scripts/ /app/scripts/
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh /app/scripts/*.py

EXPOSE 8081

# Hits nginx /health (always up, no dependency on download/pmtiles)
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -q --spider http://localhost:${PORT:-8081}/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]


# ── Stage 2: baker — bake versioned demo tiles at build time ─────
FROM base AS baker

ARG PROTOMAPS_URL="https://build.protomaps.com/20260404.pmtiles"
ARG BAKE_DEMO_AREA="-13.0,39.3,-4.5,42.6"
ARG BAKE_LOWZOOM_MAXZOOM="5"

# Bake into /data-prebuilt/demo/ (NOT /data/) so the volume mount at /data
# does not shadow the pre-baked tiles at runtime.
#
# Versioned naming: extract the YYYYMMDD date from the URL and use it
# as the tile filename.  Symlinks planet.pmtiles / world-lowzoom.pmtiles
# point to the versioned files.
RUN mkdir -p /data-prebuilt/demo && \
    BUILD_DATE=$(echo "$PROTOMAPS_URL" | grep -oE '[0-9]{8}') && \
    echo "Baking demo tiles: date=${BUILD_DATE}, bbox=${BAKE_DEMO_AREA}, source=${PROTOMAPS_URL}" && \
    pmtiles extract "$PROTOMAPS_URL" "/data-prebuilt/demo/${BUILD_DATE}.pmtiles" \
      --bbox="$BAKE_DEMO_AREA" --maxzoom=15 && \
    touch "/data-prebuilt/demo/${BUILD_DATE}.pmtiles.done" && \
    pmtiles extract "$PROTOMAPS_URL" "/data-prebuilt/demo/${BUILD_DATE}-lowzoom.pmtiles" \
      --maxzoom="$BAKE_LOWZOOM_MAXZOOM" && \
    touch "/data-prebuilt/demo/${BUILD_DATE}-lowzoom.pmtiles.done" && \
    ln -s "${BUILD_DATE}.pmtiles" /data-prebuilt/demo/planet.pmtiles && \
    ln -s "${BUILD_DATE}-lowzoom.pmtiles" /data-prebuilt/demo/world-lowzoom.pmtiles && \
    echo "Demo tiles baked successfully:" && \
    ls -lh /data-prebuilt/demo/


# ── Stage 3: final — merge base + baked data ────────────────────────
FROM base AS final

# Pre-built tiles — orchestrator seeds them into /data/{scope}/ on kickoff
COPY --from=baker /data-prebuilt /data-prebuilt
