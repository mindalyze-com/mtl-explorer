# Home Container Build

For the normal installation path with prebuilt images, follow the
[README quick start](../../README.md#quick-start). This page is only for building
MTL Explorer Docker images locally from a source checkout.

The container build runs the Maven reactor inside Docker. That includes
OpenAPI TypeScript client generation and the Vue production build. It creates
images in your local Docker daemon; it does not replace normal Maven, npm, IDE,
or frontend dev-server workflows. Run Java/Maven development commands inside
the existing development container; do not run the backend on the host.

The runtime includes the Spring Boot application JAR and the named GPS diagnostics
module JAR. Custom launchers must preserve their module-path arguments; see
[GPS quality modules](gps-quality-modules.md).

The app installs precompiled libvips, ImageMagick, FFmpeg and libheif from conda-forge.
Image previews use libvips first, with a logged and signaled ImageMagick fallback
when libvips has no native loader for the input format.
Separate AMD64 and ARM64 lock files pin every package and SHA-256 checksum.
The runtime build checks the public iOS 18 HEIC regression, video thumbnails
and H.264/AAC streaming. See [media packages and tests](../../docker/media/README.md)
for versions, lock updates and focused build commands. GPSBabel still builds
from source.

The Garmin installer uses `bsdtar` (`libarchive-tools`) for source archives.
Ubuntu 26.04's GNU tar can fail with `Function not implemented`
when extracting nested paths in amd64 builds under OrbStack/Rosetta;
see [OrbStack issue 2588](https://github.com/orbstack/orbstack/issues/2588).

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
folders, and update commands described in [Administration](../administration/README.md).

## Publishing an app release

Commit source changes, increment `APP_VERSION_TAG` in `docker-build-deploy.py`,
and commit that version before running `./docker-build-deploy.py --yes --app-only`.
This publishes the version and `alpha` for AMD64 and ARM64; add `--dry-run` to preview.

App releases build from a fixed Git archive, excluding uncommitted run evidence.
The helper rejects other pending changes and existing app version tags, embeds
the full Git revision and tree in image labels and manifest annotations, and
records their image digest in the private checkout's
`docs-my/operations/releases/builds/` directory. Commit the app record
`mytraillog-BUILD.json` and component manifest `mtl-bundle-BUILD.json` there.
These small records are durable release metadata, kept outside the public source
checkout. The digest identifies the exact built image; the Git revision identifies
its source, not downloaded dependencies.

The default private checkout is `../mtl-explorer-private`; `MTL_PRIVATE_REPO`
overrides its location. Without that checkout, pass
`--release-record-dir /path/to/private-release-records` to choose a persistent
directory outside the application checkout. The helper shows the directory in
its plan, checks it before publishing, and refuses to overwrite existing records.
Dry runs write nothing. Channel-only promotion uses the existing images and does
not require a record directory. Old files under `logs/releases/` are historical;
use the records generated for the tested build.

About shows the revision baked into the frontend, and Settings → System
information shows the server's revision from `MTL_IMAGE_REVISION` or
`/opt/mtl/image-revision`. Both show an eight-character hash beside the version;
click it to reveal the full revision. System information offers “Copy all versions”
with a preview of client, server, database, runtime, and sidecar versions. The
report excludes logs, configuration, URLs, paths, and installation identifiers.
Builds without revision metadata show the version alone.

Read the revision with `docker buildx imagetools inspect wauwau0977/mytraillog:alpha`,
then compare source with `git diff <image-revision> HEAD` in this workspace.
Use `image@sha256:…` when a test must keep using the same image as a channel moves.

## Optional local map image

Build and use the map sidecar image only if you want to test the local map
container too:

```bash
docker buildx build --load -t mytraillog-maps:local docker-maps
MTL_APP_IMAGE=mytraillog:local \
MTL_BROUTER_IMAGE=mytraillog-brouter:local \
MTL_LOCATION_SEARCH_IMAGE=mytraillog-location-search:local \
MTL_MAP_IMAGE=mytraillog-maps:local \
MTL_IMAGE_PULL_POLICY=never \
MTL_UPDATES_MAP_SERVER_MANAGEMENT=SELF_MANAGED \
docker compose --profile local-maps up -d
```

The `local-maps` profile downloads about 130 GB of map data on first startup.
For the simpler hosted-map setup, leave this profile disabled and follow the
[README quick start](../../README.md#quick-start).
