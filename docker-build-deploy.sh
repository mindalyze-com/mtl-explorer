#!/bin/bash

## cd /mnt/c/Users/patri/IdeaProjects/mytraillog/
## cd  /Users/pheusser/IdeaProjects/mytraillog/


#   Pass --no-cache when you explicitly want to pull fresh OS packages, e.g.:
#
#   Example:
#     ./docker-build-deploy.sh --no-cache
#     mvn clean install  -DskipTests=true && ./docker-build-deploy.sh


# if you get cert errors on build:
# ERROR: failed to solve: postgis/postgis:15-3.4: failed to authorize: failed to fetch oauth token: Post "https://auth.docker.io/token": tls: failed to verify certificate: x509: certificate signed by unknown authority
# --> sudo apt-get install --reinstall ca-certificates

# Variables
DOCKER_USERNAME="wauwau0977"
IMAGE_NAME="mytraillog"
TAG_NAME="1.191"  # latest  or version, ( v. 1.1 -> 2025-10 release)

MAP_IMAGE_NAME="mytraillog-maps"
MAP_TAG_NAME="1.31"

BROUTER_IMAGE_NAME="mytraillog-brouter"
BROUTER_TAG_NAME="1.0"


# Build result tracking
BUILD_RESULTS=()

record_result() {
  local label="$1"
  local exit_code="$2"
  if [ "$exit_code" -eq 0 ]; then
    BUILD_RESULTS+=("OK|$label")
  else
    BUILD_RESULTS+=("FAIL|$label")
  fi
}

# Parse optional flags
NO_CACHE_FLAG=""
SKIP_MAVEN=false
for arg in "$@"; do
  case "$arg" in
    --no-cache)
      NO_CACHE_FLAG="--no-cache"
      echo "⚠️  --no-cache: all layers will be rebuilt from scratch."
      ;;
    --skip-maven)
      SKIP_MAVEN=true
      echo "⚠️  --skip-maven: skipping Maven build."
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: $0 [--no-cache] [--skip-maven]"
      exit 1
      ;;
  esac
done


# Step -1: Build Maven parent project (unless --skip-maven)
if [ "$SKIP_MAVEN" = false ]; then
  echo "Building Maven parent project (skipping tests)..."
  mvn clean install -DskipTests=true
  record_result "Maven build" $?
else
  record_result "Maven build (skipped)" 0
fi

# Step 0: Remove previous images (ignore errors if images don't exist locally)
docker rmi $IMAGE_NAME:$TAG_NAME || true
docker rmi $DOCKER_USERNAME/$IMAGE_NAME || true
docker rmi $MAP_IMAGE_NAME:$MAP_TAG_NAME || true
docker rmi $DOCKER_USERNAME/$MAP_IMAGE_NAME || true
docker rmi $BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME || true
docker rmi $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME || true

# Step 0.5: Setup Docker Buildx for multi-platform builds.
# Ensures a docker-container driver builder exists (required for --platform linux/amd64,linux/arm64).
# The default OrbStack/Docker Desktop driver does not support multi-platform --push.
if ! docker buildx ls | grep -q 'multi-builder'; then
  echo "Creating multi-platform buildx builder..."
  docker buildx create --name multi-builder --driver docker-container --bootstrap
fi
docker buildx use multi-builder

# Step 0.6: Verify the Spring Boot JAR exists before starting a long build
if [ ! -f "mtl-server/target/mtl-server-0.0.1-SNAPSHOT.jar" ]; then
  echo "ERROR: mtl-server JAR not found. Run 'mvn package -DskipTests' in mtl-server/ first."
  exit 1
fi

# Step 1: Login to Docker Hub early (needed for the map server --push step below)
echo "Login to Docker Hub... Using implicit user $DOCKER_USERNAME which allows caching credentials, but only if user not given."
docker login

# Step 2a: Build and load the main app image (single platform — --load requires single arch)
echo "Building main app Docker image..."
docker buildx build $NO_CACHE_FLAG --platform linux/amd64 -t $IMAGE_NAME --load .
record_result "Build $IMAGE_NAME:$TAG_NAME" $?

# Step 2b: Build and push the map server image directly to Docker Hub.
# --push supports multi-platform manifests (amd64 for NAS, arm64 for Mac/Pi).
# We skip --load here because Docker cannot load multi-platform images into the local daemon.

echo "Resolving latest Protomap daily build for demo tiles..."
LATEST_PMTILES=""
for DAYS_AGO in $(seq 0 59); do
  # Try both GNU and BSD date formats
  DATE_STR=$(date -u -d "-${DAYS_AGO} days" '+%Y%m%d' 2>/dev/null || date -u -v-${DAYS_AGO}d '+%Y%m%d' 2>/dev/null)
  CANDIDATE_URL="${PROTOMAPS_BUILDS_BASE_URL:-https://build.protomaps.com}/${DATE_STR}.pmtiles"
  if curl -sI --fail "$CANDIDATE_URL" > /dev/null; then
    LATEST_PMTILES="$CANDIDATE_URL"
    echo "Found active build: $LATEST_PMTILES"
    break
  fi
done

if [ -z "$LATEST_PMTILES" ]; then
  echo "ERROR: Could not resolve recent daily build from Protomaps (checked 60 days)."
  exit 1
fi

echo "Building and pushing map server Docker image (multi-platform: amd64 + arm64)..."
docker buildx build $NO_CACHE_FLAG \
  --build-arg PROTOMAPS_URL="${LATEST_PMTILES}" \
  --platform linux/amd64,linux/arm64 \
  -t $DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME \
  --push \
  ./docker-maps
record_result "Build+Push $MAP_IMAGE_NAME:$MAP_TAG_NAME" $?

# Step 2c: Build and push the BRouter sidecar image (multi-platform: amd64 + arm64).
echo "Building and pushing BRouter sidecar Docker image (multi-platform: amd64 + arm64)..."
docker buildx build $NO_CACHE_FLAG \
  --platform linux/amd64,linux/arm64 \
  -t $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME \
  --push \
  ./docker-brouter
record_result "Build+Push $BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME" $?

# Step 3: Tag and push the main app image
echo "Tagging the main app image..."
docker tag $IMAGE_NAME $DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME

echo "Pushing main app image to Docker Hub..."
docker push $DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME
record_result "Push $IMAGE_NAME:$TAG_NAME" $?

docker pull $DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME
docker pull $DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME
docker pull $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME

# docker image prune --all

# ─── Build Summary ───────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                        BUILD SUMMARY                            ║"
echo "╚══════════════════════════════════════════════════════════════════╝"

# Settings used
echo ""
echo "  Settings:"
if [ "$SKIP_MAVEN" = false ]; then
  echo "    🔨  Maven build:    yes"
else
  echo "    ⏭️   Maven build:    skipped (--skip-maven)"
fi
if [ -n "$NO_CACHE_FLAG" ]; then
  echo "    🚫  Docker cache:   disabled (--no-cache)"
else
  echo "    📦  Docker cache:   enabled"
fi
echo "    🏷️   App image:      $DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME"
echo "    🗺️   Map image:      $DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME"
echo "    🛣️   Brouter image:  $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME"
echo "    🌍  Protomaps tile:  ${LATEST_PMTILES:-n/a}"

# Step results
echo ""
echo "  Steps:"
OVERALL_OK=true
for entry in "${BUILD_RESULTS[@]}"; do
  status="${entry%%|*}"
  label="${entry#*|}"
  if [ "$status" = "OK" ]; then
    echo "    ✅  $label"
  else
    echo "    ❌  $label"
    OVERALL_OK=false
  fi
done

# Image sizes
echo ""
echo "  Images:"
for img_ref in \
    "$DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME" \
    "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME" \
    "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME"; do
  SIZE=$(docker image ls --format '{{.Size}}' "$img_ref" 2>/dev/null)
  IMAGE_ID=$(docker image ls --format '{{.ID}}' "$img_ref" 2>/dev/null)
  if [ -n "$SIZE" ]; then
    echo "    🐳  $img_ref"
    echo "        ID: $IMAGE_ID   Size: $SIZE"
  else
    echo "    🐳  $img_ref   (not found in local daemon)"
  fi
done

echo ""
if [ "$OVERALL_OK" = true ]; then
  echo "  🎉  All steps completed successfully!"
else
  echo "  ⚠️   One or more steps FAILED — review output above."
fi
echo "═══════════════════════════════════════════════════════════════════"
echo ""

# to run:
# docker run -p 8080:8080 -d wauwau0977/mytraillog:0.2
# docker run -p 8080:8080 -d wauwau0977/mytraillog:latest
# docker run --volume=/Users/pheusser/Documents/GPX_WATCHER_3A:/app/gpx -p 15432:5432 -p 18080:8080 -d wauwau0977/mytraillog:beta

# TAG versions and push as latest
# docker tag wauwau0977/mytraillog:0.5 wauwau0977/mytraillog:latest
# docker push wauwau0977/mytraillog:latest