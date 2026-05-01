#!/bin/bash

## cd /mnt/c/Users/patri/IdeaProjects/mytraillog/
## cd  /Users/pheusser/IdeaProjects/mytraillog/


#   Pass --no-cache when you explicitly want to pull fresh OS packages, e.g.:
#
#   Example:
#     ./docker-build-deploy.sh --no-cache
#     mvn clean install  -DskipTests=true && ./docker-build-deploy.sh
#
#   Release channels are opt-in:
#     ./docker-build-deploy.sh --publish-beta
#     ./docker-build-deploy.sh --publish-latest
#     ./docker-build-deploy.sh --tag-beta-only
#
#   Multi-platform app build on Mac/Buildx:
#     ./docker-build-deploy.sh --multi-platform-app --publish-beta


# if you get cert errors on build:
# ERROR: failed to solve: postgis/postgis:15-3.4: failed to authorize: failed to fetch oauth token: Post "https://auth.docker.io/token": tls: failed to verify certificate: x509: certificate signed by unknown authority
# --> sudo apt-get install --reinstall ca-certificates

# Variables
DOCKER_USERNAME="wauwau0977"
LATEST_TAG="latest"
BETA_TAG="beta"
IMAGE_NAME="mytraillog"
TAG_NAME="1.225"  # version tag, ( v. 1.1 -> 2025-10 release)

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
PUBLISH_LATEST=false
PUBLISH_BETA=false
TAG_ONLY_CHANNEL=""
TAG_ONLY_REQUESTS=0
MULTI_PLATFORM_APP=false
APP_PLATFORMS="linux/amd64,linux/arm64"

usage() {
  echo "Usage: $0 [--no-cache] [--skip-maven] [--publish-latest] [--publish-beta] [--tag-latest-only] [--tag-beta-only] [--multi-platform-app]"
}

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
    --publish-latest)
      PUBLISH_LATEST=true
      echo "Publishing stable channel tag :$LATEST_TAG in addition to version tags."
      ;;
    --publish-beta)
      PUBLISH_BETA=true
      echo "Publishing beta channel tag :$BETA_TAG in addition to version tags."
      ;;
    --tag-latest-only)
      TAG_ONLY_CHANNEL="$LATEST_TAG"
      TAG_ONLY_REQUESTS=$((TAG_ONLY_REQUESTS + 1))
      echo "Only retagging current version images as :$LATEST_TAG."
      ;;
    --tag-beta-only)
      TAG_ONLY_CHANNEL="$BETA_TAG"
      TAG_ONLY_REQUESTS=$((TAG_ONLY_REQUESTS + 1))
      echo "Only retagging current version images as :$BETA_TAG."
      ;;
    --multi-platform-app)
      MULTI_PLATFORM_APP=true
      echo "Building and pushing app image for: $APP_PLATFORMS."
      ;;
    *)
      echo "Unknown argument: $arg"
      usage
      exit 1
      ;;
  esac
done

if [ "$TAG_ONLY_REQUESTS" -gt 1 ]; then
  echo "ERROR: Use only one tag-only option."
  exit 1
fi

if [ -n "$TAG_ONLY_CHANNEL" ] && { [ "$PUBLISH_LATEST" = true ] || [ "$PUBLISH_BETA" = true ] || [ "$MULTI_PLATFORM_APP" = true ] || [ -n "$NO_CACHE_FLAG" ] || [ "$SKIP_MAVEN" = true ]; }; then
  echo "ERROR: Tag-only options cannot be combined with build options."
  exit 1
fi

CHANNEL_TAGS=()
if [ "$PUBLISH_LATEST" = true ]; then
  CHANNEL_TAGS+=("$LATEST_TAG")
fi
if [ "$PUBLISH_BETA" = true ]; then
  CHANNEL_TAGS+=("$BETA_TAG")
fi

describe_tags() {
  local description="$1"
  for channel in "${CHANNEL_TAGS[@]}"; do
    description="$description + $channel"
  done
  echo "$description"
}

publish_channel_from_version_tags() {
  local channel="$1"

  echo "Publishing :$channel from current version tags..."
  docker buildx imagetools create -t "$DOCKER_USERNAME/$IMAGE_NAME:$channel" "$DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME" || exit 1
  docker buildx imagetools create -t "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$channel" "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME" || exit 1
  docker buildx imagetools create -t "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$channel" "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME" || exit 1

  echo "Verifying :$channel manifests..."
  docker buildx imagetools inspect "$DOCKER_USERNAME/$IMAGE_NAME:$channel" >/dev/null || exit 1
  docker buildx imagetools inspect "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$channel" >/dev/null || exit 1
  docker buildx imagetools inspect "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$channel" >/dev/null || exit 1

  echo "Published :$channel for app $TAG_NAME, maps $MAP_TAG_NAME, and BRouter $BROUTER_TAG_NAME."
}

if [ -n "$TAG_ONLY_CHANNEL" ]; then
  echo "Using existing Docker credentials for Docker Hub."
  publish_channel_from_version_tags "$TAG_ONLY_CHANNEL"
  exit 0
fi

APP_REMOTE_REFS=("$DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME")
MAP_TAG_ARGS=(-t "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME")
MAP_REMOTE_REFS=("$DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME")
BROUTER_TAG_ARGS=(-t "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME")
BROUTER_REMOTE_REFS=("$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME")

for channel in "${CHANNEL_TAGS[@]}"; do
  APP_REMOTE_REFS+=("$DOCKER_USERNAME/$IMAGE_NAME:$channel")
  MAP_TAG_ARGS+=(-t "$DOCKER_USERNAME/$MAP_IMAGE_NAME:$channel")
  MAP_REMOTE_REFS+=("$DOCKER_USERNAME/$MAP_IMAGE_NAME:$channel")
  BROUTER_TAG_ARGS+=(-t "$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$channel")
  BROUTER_REMOTE_REFS+=("$DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$channel")
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
docker rmi $DOCKER_USERNAME/$IMAGE_NAME:$TAG_NAME || true
docker rmi $MAP_IMAGE_NAME:$MAP_TAG_NAME || true
docker rmi $DOCKER_USERNAME/$MAP_IMAGE_NAME:$MAP_TAG_NAME || true
docker rmi $BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME || true
docker rmi $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$BROUTER_TAG_NAME || true
for channel in "${CHANNEL_TAGS[@]}"; do
  docker rmi $DOCKER_USERNAME/$IMAGE_NAME:$channel || true
  docker rmi $DOCKER_USERNAME/$MAP_IMAGE_NAME:$channel || true
  docker rmi $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$channel || true
done

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

# Step 2a: Build the main app image.
if [ "$MULTI_PLATFORM_APP" = true ]; then
  APP_TAG_ARGS=()
  for ref in "${APP_REMOTE_REFS[@]}"; do
    APP_TAG_ARGS+=(-t "$ref")
  done

  echo "Building and pushing main app Docker image (multi-platform: $APP_PLATFORMS)..."
  docker buildx build $NO_CACHE_FLAG \
    --platform "$APP_PLATFORMS" \
    "${APP_TAG_ARGS[@]}" \
    --push \
    .
  record_result "Build+Push $IMAGE_NAME:$(describe_tags "$TAG_NAME")" $?
else
  echo "Building main app Docker image (single platform: linux/amd64)..."
  docker buildx build $NO_CACHE_FLAG --platform linux/amd64 -t $IMAGE_NAME --load .
  record_result "Build $IMAGE_NAME:$TAG_NAME" $?
fi

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
  "${MAP_TAG_ARGS[@]}" \
  --push \
  ./docker-maps
record_result "Build+Push $MAP_IMAGE_NAME:$(describe_tags "$MAP_TAG_NAME")" $?

# Step 2c: Build and push the BRouter sidecar image (multi-platform: amd64 + arm64).
echo "Building and pushing BRouter sidecar Docker image (multi-platform: amd64 + arm64)..."
docker buildx build $NO_CACHE_FLAG \
  --platform linux/amd64,linux/arm64 \
  "${BROUTER_TAG_ARGS[@]}" \
  --push \
  ./docker-brouter
record_result "Build+Push $BROUTER_IMAGE_NAME:$(describe_tags "$BROUTER_TAG_NAME")" $?

# Step 3: Push the main app image when it was loaded locally.
if [ "$MULTI_PLATFORM_APP" = false ]; then
  echo "Tagging and pushing the main app image..."
  for ref in "${APP_REMOTE_REFS[@]}"; do
    docker tag $IMAGE_NAME "$ref"
    docker push "$ref"
    record_result "Push $ref" $?
  done
fi

for ref in "${APP_REMOTE_REFS[@]}" "${MAP_REMOTE_REFS[@]}" "${BROUTER_REMOTE_REFS[@]}"; do
  docker pull "$ref"
done

# docker image prune --all

# ─── Build Summary ───────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                        BUILD SUMMARY                            ║"
echo "╚══════════════════════════════════════════════════════════════════╝"

# Settings used
echo ""
echo "  Settings:"
CHANNEL_SUMMARY="${CHANNEL_TAGS[*]}"
if [ -z "$CHANNEL_SUMMARY" ]; then
  CHANNEL_SUMMARY="(none)"
fi
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
echo "    🧪  Channels:       $CHANNEL_SUMMARY"
if [ "$MULTI_PLATFORM_APP" = true ]; then
  echo "    🏷️   App image:      $DOCKER_USERNAME/$IMAGE_NAME:$(describe_tags "$TAG_NAME") ($APP_PLATFORMS)"
else
  echo "    🏷️   App image:      $DOCKER_USERNAME/$IMAGE_NAME:$(describe_tags "$TAG_NAME") (linux/amd64)"
fi
echo "    🗺️   Map image:      $DOCKER_USERNAME/$MAP_IMAGE_NAME:$(describe_tags "$MAP_TAG_NAME")"
echo "    🛣️   Brouter image:  $DOCKER_USERNAME/$BROUTER_IMAGE_NAME:$(describe_tags "$BROUTER_TAG_NAME")"
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
for img_ref in "${APP_REMOTE_REFS[@]}" "${MAP_REMOTE_REFS[@]}" "${BROUTER_REMOTE_REFS[@]}"; do
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

# By default the script publishes only immutable version tags.
# Use --publish-latest for the stable channel, --publish-beta for the demo/beta channel,
# or --tag-latest-only / --tag-beta-only to retag existing version images without rebuilding.
