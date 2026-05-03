# ----- Self-downloading PMTiles map server -----
# Downloads the planet vector map file on first startup, then serves tiles.
# The published image does not include prebuilt map archives; it either serves
# existing files from /data or prepares them at runtime when kicked off.
#
# Architecture:
#   nginx (port 8081, public) serves /data/ tree as static files.
#   Scope directories under /data/ contain versioned files
#   (YYYYMMDD.pmtiles) + symlinks (planet.pmtiles -> YYYYMMDD.pmtiles).
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
#   docker build -t wauwau0977/mytraillog-maps:1.4 .

FROM alpine:3.21

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
    tar gzip \
    # persistent nginx log rotation
    logrotate dcron

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
RUN mkdir -p /data/prod /app/scripts
VOLUME /data

# nginx: replace default config with our reverse-proxy config
RUN rm -f /etc/nginx/http.d/default.conf
COPY nginx.conf /etc/nginx/http.d/map-server.conf
COPY logrotate-mtl-maps.conf /etc/logrotate.d/mtl-maps
RUN chmod 644 /etc/logrotate.d/mtl-maps && \
    printf '17 3 * * * /usr/sbin/logrotate -s /data/logs/logrotate.status /etc/logrotate.d/mtl-maps\n' > /etc/crontabs/root

COPY scripts/ /app/scripts/
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh /app/scripts/*.py

EXPOSE 8081

# Hits nginx /health (always up, no dependency on download/pmtiles)
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -q --spider http://localhost:${PORT:-8081}/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
