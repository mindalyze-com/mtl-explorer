#!/usr/bin/env bash
# deploy-hetzner.sh — Deploy a MyTrailLog Demo instance on a Hetzner Debian server.
#
# Supports MULTIPLE instances on the SAME server, each with its own subdomain.
# A shared Caddy reverse proxy dispatches by hostname to each app stack.
#
# Architecture:
#   Internet → :80/:443 (shared Caddy)
#                ├── mtl-demo.mindalyze.com       → {slug}-app:8080
#                ├── mtl-demo-small.mindalyze.com  → {slug}-app:8080
#                └── mtl-demo-large.mindalyze.com  → {slug}-app:8080
#
#   Shared services (one instance per server, all domains share):
#     Caddy      — TLS termination + reverse proxy
#     map-server — Protomaps tile server (Portugal bbox, ~200-400 MB)
#     location-search — GeoNames SQLite FTS sidecar
#     BRouter    — route planner engine
#
# On-disk layout:
#   /opt/mtl/shared/                  ← shared services stack (Caddy + maps + search + BRouter)
#       docker-compose.yml
#       Caddyfile
#       sites/{domain}.caddy          ← per-domain snippet (auto-generated)
#   /opt/mtl/{domain}/                ← per-domain app stack (db + app only)
#       docker-compose.yml
#   /data/shared/                     ← shared persistent data
#       caddy/data/ caddy/config/     ← TLS certs + ACME state
#       maps/                         ← Portugal bbox PMTiles (~200-400 MB)
#       brouter-segments/             ← BRouter rd5 segment cache
#   /data/{domain}/                   ← per-domain persistent data
#       PostGIS/ GPX/ media/ logs/
#
# Usage:
#   scp deploy-hetzner.sh root@<server-ip>:~/
#   ssh root@<server-ip>
#   chmod +x deploy-hetzner.sh
#
#   # First domain (also installs Docker, firewall, shared Caddy):
#   ./deploy-hetzner.sh mtl-demo.mindalyze.com
#
#   # Additional domains (reuses existing infrastructure):
#   ./deploy-hetzner.sh mtl-demo-small.mindalyze.com --demo-tracks 500 --demo-photos 50
#   ./deploy-hetzner.sh mtl-demo-large.mindalyze.com --demo-tracks 50000 --demo-photos 2000
#   ./deploy-hetzner.sh mtl-demo-beta.mindalyze.com --app-tag beta --demo-tracks 1042 --demo-photos 100
#
#   # List running instances:
#   ./deploy-hetzner.sh --list
#
#   # Remove an instance:
#   ./deploy-hetzner.sh --remove mtl-demo-small.mindalyze.com

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────

DOCKER_USERNAME="wauwau0977"
DEFAULT_APP_TAG="1.271"
DEFAULT_MAP_TAG="1.71"
DEFAULT_LOCATION_SEARCH_TAG="1.1"
DEFAULT_BROUTER_TAG="1.10"
DB_IMAGE="postgis/postgis:18-3.6"
CADDY_IMAGE="caddy:2-alpine"

MTL_BASE="/opt/mtl"
SHARED_DIR="${MTL_BASE}/shared"
CADDY_SITES="${SHARED_DIR}/sites"
SHARED_DATA="/data/shared"
NETWORK_NAME="mtl-net"

# ── Colours ────────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
fail()  { echo -e "${RED}[FAIL]${NC}  $*"; exit 1; }

# ── Pre-flight ─────────────────────────────────────────────────────────────────

[[ $EUID -eq 0 ]] || fail "This script must be run as root."

# ── Helper: domain → slug (dots/hyphens-safe container prefix) ─────────────────

domain_to_slug() {
    echo "$1" | sed 's/\./-/g'
}

# ── Command: --list ────────────────────────────────────────────────────────────

if [[ "${1:-}" == "--list" ]]; then
    echo -e "${BOLD}Deployed MTL instances:${NC}"
    if [[ -d "${CADDY_SITES}" ]]; then
        shopt -s nullglob
        for f in "${CADDY_SITES}"/*.caddy; do
            [[ -f "$f" ]] || continue
            domain=$(basename "$f" .caddy)
            slug=$(domain_to_slug "$domain")
            # Check if the app container is running (compose auto-names: {project}-app-1)
            status=$(docker ps --filter "label=com.docker.compose.project=${slug}" --filter "label=com.docker.compose.service=app" --format '{{.Status}}' 2>/dev/null)
            [[ -z "$status" ]] && status="not running"
            echo -e "  ${CYAN}${domain}${NC}  →  https://${domain}/mtl/  [${status}]"
        done
    else
        echo "  (none)"
    fi
    exit 0
fi

# ── Command: --remove ─────────────────────────────────────────────────────────

if [[ "${1:-}" == "--remove" ]]; then
    [[ $# -ge 2 ]] || fail "Usage: $0 --remove <domain>"
    DOMAIN="$2"
    SLUG=$(domain_to_slug "$DOMAIN")
    INSTALL_DIR="${MTL_BASE}/${DOMAIN}"
    DATA_DIR="/data/${DOMAIN}"

    info "Removing stack for ${DOMAIN}..."
    if [[ -d "${INSTALL_DIR}" ]]; then
        cd "${INSTALL_DIR}" && docker compose -p "${SLUG}" down --remove-orphans 2>/dev/null || true
    fi
    rm -f "${CADDY_SITES}/${DOMAIN}.caddy"

    # Reload Caddy to drop the domain
    if docker ps -q -f name=mtl-caddy &>/dev/null; then
        docker exec mtl-caddy caddy reload --config /etc/caddy/Caddyfile 2>/dev/null || true
    fi

    warn "Stack stopped and Caddy config removed."
    warn "Data in ${DATA_DIR} was NOT deleted. Remove manually if desired:"
    warn "  rm -rf ${DATA_DIR} ${INSTALL_DIR}"
    exit 0
fi

# ── Parse arguments ────────────────────────────────────────────────────────────

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <domain> [--demo-tracks N] [--demo-photos N] [--app-tag TAG] [--map-tag TAG] [--location-search-tag TAG] [--brouter-tag TAG]"
    echo "       $0 --list"
    echo "       $0 --remove <domain>"
    echo ""
    echo "Options:"
    echo "  --app-tag TAG              Docker tag for wauwau0977/mytraillog (default: ${DEFAULT_APP_TAG})."
    echo "  --map-tag TAG              Docker tag for wauwau0977/mytraillog-maps (default: ${DEFAULT_MAP_TAG})."
    echo "  --location-search-tag TAG  Docker tag for wauwau0977/mytraillog-location-search (default: ${DEFAULT_LOCATION_SEARCH_TAG})."
    echo "  --brouter-tag TAG          Docker tag for wauwau0977/mytraillog-brouter (default: ${DEFAULT_BROUTER_TAG})."
    echo ""
    echo "Examples:"
    echo "  $0 mtl-demo.mindalyze.com"
    echo "  $0 mtl-demo-small.mindalyze.com --demo-tracks 500 --demo-photos 50"
    echo "  $0 mtl-demo-large.mindalyze.com --demo-tracks 50000 --demo-photos 2000"
    echo "  $0 mtl-demo-beta.mindalyze.com --app-tag beta --demo-tracks 1042 --demo-photos 100"
    exit 1
fi

DOMAIN="$1"
shift
SLUG=$(domain_to_slug "$DOMAIN")
INSTALL_DIR="${MTL_BASE}/${DOMAIN}"
DATA_DIR="/data/${DOMAIN}"

# Optional overrides
DEMO_TRACK_COUNT="10000"
DEMO_PHOTO_COUNT="500"
APP_TAG="${DEFAULT_APP_TAG}"
MAP_TAG="${DEFAULT_MAP_TAG}"
LOCATION_SEARCH_TAG="${DEFAULT_LOCATION_SEARCH_TAG}"
BROUTER_TAG="${DEFAULT_BROUTER_TAG}"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --demo-tracks) DEMO_TRACK_COUNT="$2"; shift 2 ;;
        --demo-photos) DEMO_PHOTO_COUNT="$2"; shift 2 ;;
        --app-tag) APP_TAG="$2"; shift 2 ;;
        --map-tag) MAP_TAG="$2"; shift 2 ;;
        --location-search-tag) LOCATION_SEARCH_TAG="$2"; shift 2 ;;
        --brouter-tag) BROUTER_TAG="$2"; shift 2 ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

APP_IMAGE="${DOCKER_USERNAME}/mytraillog:${APP_TAG}"
MAP_IMAGE="${DOCKER_USERNAME}/mytraillog-maps:${MAP_TAG}"
LOCATION_SEARCH_IMAGE="${DOCKER_USERNAME}/mytraillog-location-search:${LOCATION_SEARCH_TAG}"
BROUTER_IMAGE="${DOCKER_USERNAME}/mytraillog-brouter:${BROUTER_TAG}"

info "Deploying ${DOMAIN} (slug: ${SLUG})"
info "  tracks=${DEMO_TRACK_COUNT}, photos=${DEMO_PHOTO_COUNT}"
info "  app=${APP_IMAGE}"
info "  shared: maps=${MAP_IMAGE}, location-search=${LOCATION_SEARCH_IMAGE}, brouter=${BROUTER_IMAGE}"
echo ""

# ── Step 1: System update ─────────────────────────────────────────────────────

info "Step 1/6 — Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq curl git ufw ca-certificates gnupg
ok "System updated"

# ── Step 2: Install Docker ────────────────────────────────────────────────────

if command -v docker &>/dev/null; then
    ok "Step 2/6 — Docker already installed: $(docker --version)"
else
    info "Step 2/6 — Installing Docker Engine..."

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    systemctl enable docker
    systemctl start docker
    ok "Docker installed: $(docker --version)"
fi

# Verify compose plugin
docker compose version &>/dev/null || fail "Docker Compose plugin not found"
ok "Docker Compose: $(docker compose version)"

# ── Step 3: Firewall ──────────────────────────────────────────────────────────

info "Step 3/6 — Configuring firewall..."
ufw allow OpenSSH   >/dev/null 2>&1
ufw allow 80/tcp    >/dev/null 2>&1
ufw allow 443/tcp   >/dev/null 2>&1
ufw allow 443/udp   >/dev/null 2>&1  # HTTP/3
ufw --force enable   >/dev/null 2>&1
ok "Firewall configured (SSH, HTTP, HTTPS)"

# ── Step 4: Shared Docker network ──────────────────────────────────────────────

info "Step 4/8 — Ensuring shared Docker network '${NETWORK_NAME}'..."
docker network inspect "${NETWORK_NAME}" &>/dev/null || docker network create "${NETWORK_NAME}"
ok "Network '${NETWORK_NAME}' ready"

# ── Step 5: Shared Caddy reverse proxy ─────────────────────────────────────────

info "Step 5/8 — Ensuring shared services (Caddy + maps + location search + BRouter)..."
mkdir -p "${SHARED_DIR}" "${CADDY_SITES}"
mkdir -p "${SHARED_DATA}"/{caddy/data,caddy/config,maps,brouter-segments}

# Global Caddyfile — imports all per-domain snippets
cat > "${SHARED_DIR}/Caddyfile" <<'GLOBAL_CADDY_EOF'
# Global Caddyfile — auto-managed by deploy-hetzner.sh
# Each domain gets its own file in /etc/caddy/sites/{domain}.caddy
{
	# Global options
}

import /etc/caddy/sites/*.caddy
GLOBAL_CADDY_EOF

# Shared services compose stack: Caddy + map-server + location-search + BRouter
cat > "${SHARED_DIR}/docker-compose.yml" <<SHARED_COMPOSE_EOF
services:
  caddy:
    image: ${CADDY_IMAGE}
    container_name: mtl-caddy
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./sites:/etc/caddy/sites:ro
      - ${SHARED_DATA}/caddy/data:/data
      - ${SHARED_DATA}/caddy/config:/config
    networks:
      - ${NETWORK_NAME}
    restart: unless-stopped

  map-server:
    image: ${MAP_IMAGE}
    container_name: mtl-maps
    expose:
      - 8081
    environment:
      PORT: "8081"
      LOWZOOM_MAXZOOM: "5"
      # Download only the Portugal/Porto area (~250 MB) instead of the full planet (~120 GB).
      # bbox format: west,south,east,north — matches BAKE_DEMO_AREA in docker-maps Dockerfile
      MAP_DOWNLOAD_URL: "latest"
      MAP_AREA_BBOX: "-13.0,39.3,-4.5,42.6"
    volumes:
      - ${SHARED_DATA}/maps:/data
    networks:
      ${NETWORK_NAME}:
        aliases:
          - mtl-maps
    restart: unless-stopped

  location-search:
    image: ${LOCATION_SEARCH_IMAGE}
    container_name: mtl-location-search
    pull_policy: always
    expose:
      - 8083
    environment:
      PORT: "8083"
      MTL_LOCATION_SEARCH_DB: /data/geonames-search.sqlite
    networks:
      ${NETWORK_NAME}:
        aliases:
          - mtl-location-search
    deploy:
      resources:
        limits:
          memory: 256m
        reservations:
          memory: 64m
    restart: unless-stopped

  brouter:
    image: ${BROUTER_IMAGE}
    container_name: mtl-brouter
    expose:
      - 17777
      - 17778
    environment:
      BROUTER_JAVA_XMX: "512m"
    volumes:
      - ${SHARED_DATA}/brouter-segments:/segments4
    networks:
      ${NETWORK_NAME}:
        aliases:
          - mtl-brouter
    deploy:
      resources:
        limits:
          memory: 768m
        reservations:
          memory: 256m
    restart: unless-stopped

networks:
  ${NETWORK_NAME}:
    external: true
SHARED_COMPOSE_EOF

# Start or restart shared services
cd "${SHARED_DIR}"
docker compose -p mtl-shared pull --quiet
docker compose -p mtl-shared up -d
ok "Shared services running (Caddy + maps + location search + BRouter)"

# ── Step 6: Create data directories for ${DOMAIN} ─────────────────────────────

info "Step 6/8 — Creating data directories under ${DATA_DIR}..."
mkdir -p "${DATA_DIR}"/{PostGIS,GPX,media,logs}
mkdir -p "${INSTALL_DIR}"
ok "Directories created"

# ── Step 7: Write per-domain config + compose ─────────────────────────────────

info "Step 7/8 — Writing config for ${DOMAIN}..."

# Per-domain Caddy snippet
cat > "${CADDY_SITES}/${DOMAIN}.caddy" <<SITE_EOF
${DOMAIN} {
	redir / /mtl/ permanent
	reverse_proxy ${SLUG}-app:8080
	log {
		output file /data/${DOMAIN}-access.log {
			roll_size 50MiB
			roll_keep 3
		}
	}
}
SITE_EOF

# Per-domain app stack (joins the shared network via aliases for cross-project DNS)
# -p ${SLUG} handles compose project scoping (container names, labels, etc.)
# Network aliases make services discoverable by Caddy and by each other.
cat > "${INSTALL_DIR}/docker-compose.yml" <<COMPOSE_EOF
services:

  db:
    image: ${DB_IMAGE}
    environment:
      POSTGRES_DB: mtl
      POSTGRES_USER: mtluser
      POSTGRES_PASSWORD: MtlSuper123
    expose:
      - 5432
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/PostGIS:/var/lib/postgresql/18/docker
    networks:
      ${NETWORK_NAME}:
        aliases:
          - ${SLUG}-db
    deploy:
      resources:
        limits:
          memory: 1g
        reservations:
          memory: 256m
    command: >
      postgres
      -c shared_buffers=128MB
      -c effective_cache_size=512MB
      -c work_mem=8MB
      -c maintenance_work_mem=64MB
      -c max_wal_size=256MB
      -c checkpoint_completion_target=0.9
      -c wal_buffers=8MB
      -c synchronous_commit=off

  app:
    image: ${APP_IMAGE}
    pull_policy: always
    depends_on:
      - db
    environment:
      POSTGRES_DB: mtl
      POSTGRES_USER: mtluser
      POSTGRES_PASSWORD: MtlSuper123
      DEMO_MODE: "Y"
      DEMO_TARGET_TRACK_COUNT: "${DEMO_TRACK_COUNT}"
      DEMO_PHOTO_COUNT: "${DEMO_PHOTO_COUNT}"
      SERVER_SSL_ENABLED: "false"
      SERVER_FORWARD_HEADERS_STRATEGY: "native"
      # Override Spring datasource to use the domain-specific DB alias
      SPRING_DATASOURCE_URL: "jdbc:postgresql://${SLUG}-db:5432/mtl"
      # Shared map-server (single instance for all domains)
      MTL_MAP__SERVER_TILE__UPSTREAM__URL: "http://mtl-maps:8081"
      MTL_MAP__SERVER_STATUS__URL: "http://mtl-maps:8081/status"
      # Shared GeoNames location-search sidecar (single instance for all domains)
      MTL_LOCATION_SEARCH_STATUS_URL: "http://mtl-location-search:8083/status"
      MTL_LOCATION_SEARCH_QUERY_URL: "http://mtl-location-search:8083/search"
      # Shared BRouter (single instance for all domains)
      MTL_PLANNER_ENABLED: "true"
      MTL_PLANNER_BROUTER_BASE_URL: "http://mtl-brouter:17777"
      MTL_PLANNER_STATUS_URL: "http://mtl-brouter:17778/status"
    expose:
      - 8080
    restart: unless-stopped
    networks:
      ${NETWORK_NAME}:
        aliases:
          - ${SLUG}-app
    deploy:
      resources:
        limits:
          memory: 1536m
        reservations:
          memory: 512m
    volumes:
      - ${DATA_DIR}/GPX:/app/gpx
      - ${DATA_DIR}/media:/app/media
      - ${DATA_DIR}/logs:/app/logs

networks:
  ${NETWORK_NAME}:
    external: true
COMPOSE_EOF

ok "Config written to ${INSTALL_DIR}/ and ${CADDY_SITES}/${DOMAIN}.caddy"

# ── Step 8: Pull images, start stack, reload Caddy ────────────────────────────

info "Step 8/8 — Pulling images and starting ${DOMAIN} stack..."
cd "${INSTALL_DIR}"
docker compose -p "${SLUG}" pull
docker compose -p "${SLUG}" up -d

# Reload Caddy to pick up the new site config
info "Reloading Caddy to activate ${DOMAIN}..."
docker exec mtl-caddy caddy reload --config /etc/caddy/Caddyfile
ok "Caddy reloaded"

ok "Stack started!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}  MyTrailLog Demo is deploying: ${DOMAIN}${NC}"
echo ""
echo "  URL:    https://${DOMAIN}/mtl/"
echo "  Login:  demo / mtl"
echo ""
echo "  First startup takes a few minutes (DB init + demo data extraction)."
echo ""
echo "  Monitor:  cd ${INSTALL_DIR} && docker compose -p ${SLUG} logs -f"
echo "  Caddy:    docker logs mtl-caddy"
echo "  List all: $0 --list"
echo "  Remove:   $0 --remove ${DOMAIN}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
