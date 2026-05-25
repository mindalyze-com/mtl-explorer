# Home Container Build

For the normal installation path with prebuilt images, follow the
[README quick start](../README.md#quick-start). This page is only for building
MTL Explorer Docker images locally from a source checkout.

The container build runs the Maven reactor inside Docker. That includes
OpenAPI TypeScript client generation and the Vue production build. It creates
images in your local Docker daemon; it does not replace normal Maven, npm, IDE,
or frontend dev-server workflows.

## Images

The examples build:

- `mytraillog:local` - app image
- `mytraillog-brouter:local` - BRouter image
- `mytraillog-location-search:local` - GeoNames search sidecar image
- `mytraillog-maps:local` - optional local map sidecar image

These names are local Docker tags only; the product name is MTL Explorer.

## Build and run

Run from the repository root, where `docker-compose.yml` is located.

macOS, Linux, WSL, or Git Bash:

```bash
export BUILDKIT_PROGRESS=plain
docker buildx build --load -t mytraillog:local .
docker buildx build --load -t mytraillog-brouter:local docker-brouter
docker buildx build --load -t mytraillog-location-search:local -f docker-location-search/Dockerfile .

MTL_APP_IMAGE=mytraillog:local \
MTL_BROUTER_IMAGE=mytraillog-brouter:local \
MTL_LOCATION_SEARCH_IMAGE=mytraillog-location-search:local \
MTL_IMAGE_PULL_POLICY=never \
docker compose up -d
```

Windows PowerShell:

```powershell
$env:BUILDKIT_PROGRESS = "plain"
docker buildx build --load -t mytraillog:local .
docker buildx build --load -t mytraillog-brouter:local docker-brouter
docker buildx build --load -t mytraillog-location-search:local -f docker-location-search/Dockerfile .

$env:MTL_APP_IMAGE = "mytraillog:local"
$env:MTL_BROUTER_IMAGE = "mytraillog-brouter:local"
$env:MTL_LOCATION_SEARCH_IMAGE = "mytraillog-location-search:local"
$env:MTL_IMAGE_PULL_POLICY = "never"
docker compose up -d
```

For useful local location search, build
`docker-location-search/geonames-search/build/geonames-search.sqlite` first or
mount your own database at `./data/location-search/geonames-search.sqlite`.

The `MTL_*_IMAGE` variables tell Compose to use local images instead of the
published defaults. `MTL_IMAGE_PULL_POLICY=never` prevents Compose from trying
to pull those local-only tags. After startup, use the same URL, login, data
folders, and update commands described in [Home install](home-install.md).

## Optional local map image

Build and use the map sidecar image only if you want to test the local map
container too:

```bash
docker buildx build --load -t mytraillog-maps:local docker-maps
MTL_MAP_IMAGE=mytraillog-maps:local docker compose --profile local-maps up -d
```

The `local-maps` profile downloads about 130 GB of map data on first startup.
For the simpler hosted-map setup, leave this profile disabled and follow the
[README quick start](../README.md#quick-start).
