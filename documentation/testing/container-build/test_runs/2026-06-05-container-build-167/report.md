> **RESULT: PASS - Documented local-image build, Compose startup, GPX import, deletion sync, and GUI verification completed.**

# MTL Explorer Container Build Test

## Goal

Validate the MTL Explorer container-build procedure on server `167.233.16.201` using GitHub `main` from `https://github.com/mindalyze-com/mtl-explorer`.

The procedure source of truth was `documentation/container-build.md` in the checked-out GitHub `main` branch. The linked `documentation/home-install.md` / README quick-start guidance was used only where `container-build.md` delegates URL, login, and data-folder details.

No screenshots were captured.

## Timing Summary

| Phase | Start UTC | End UTC | Duration | Result |
|---|---:|---:|---:|---|
| Docker prerequisite setup | 2026-06-05 07:56:22 | 2026-06-05 07:56:34 | 12s | PASS |
| Checkout/preparation | 2026-06-05 07:56:50 | 2026-06-05 07:56:59 | 9s | PASS |
| Container build | 2026-06-05 07:57:59 | 2026-06-05 08:02:27 | 268s | PASS |
| Stack startup | 2026-06-05 08:02:59 | 2026-06-05 08:03:46 | 47s | PASS |
| GPX import sync | 2026-06-05 08:07:02 | 2026-06-05 08:07:16 | 14s | PASS |
| Deletion sync | 2026-06-05 08:08:54 | 2026-06-05 08:09:03 | 9s | PASS |
| Final technical verification | 2026-06-05 08:11:57 | 2026-06-05 08:12:01 | 4s | PASS |

Build command timings: app image `249s`, BRouter image `11s`, location-search image `8s`.

## Environment

| Item | Value |
|---|---|
| Server | `167.233.16.201` |
| Hostname | `mtl-container-build` |
| OS | Debian GNU/Linux 13 `trixie`, `DEBIAN_VERSION_FULL=13.5` |
| Kernel | `Linux 6.12.88+deb13-cloud-amd64` |
| Baseline RAM | `3.7Gi` total, `3.4Gi` available, no swap |
| Baseline disk | `/dev/sda1` `75G` total, `988M` used, `71G` available |
| Final disk | `/dev/sda1` `75G` total, `8.2G` used, `64G` available |
| GitHub commit | `2f445e409fe5054c7155851eb0560c3733f183df` |
| Commit date / subject | `2026-06-05T09:51:15+02:00` / `New features` |

Baseline missing tools: `docker`, `docker buildx`, `docker compose`, and `git`.

## Separate Host Setup

The server accepted the provided root credential but enforced an immediate root password change before non-interactive SSH could run. That access setup is not part of the MTL Explorer flow and the new secret is not recorded in this report.

Docker prerequisites were then installed because Docker Engine, Buildx, and the Compose plugin were missing:

```bash
apt-get update
apt-get install -y ca-certificates gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian %s stable\n' "$(dpkg --print-architecture)" "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
```

Installed versions:

```text
Docker version 29.5.3, build d1c06ef
github.com/docker/buildx v0.34.1 e0b0e77d18d3379bc1e0d55f3b37de288d36fe47
Docker Compose version v5.1.4
containerd containerd.io v2.2.4 193637f7ee8ae5f5aa5248f49e7baa3e6164966e
```

Git was missing and installed during checkout/preparation:

```bash
apt-get install -y git
git --version
```

Result: `git version 2.47.3`.

## Source Checkout

Commands:

```bash
git clone --branch main --single-branch https://github.com/mindalyze-com/mtl-explorer.git /root/mtl-container-build-test-2026-06-05/mtl-explorer
cd /root/mtl-container-build-test-2026-06-05/mtl-explorer
git rev-parse HEAD
sed -n '1,260p' documentation/container-build.md
```

Result: commit `2f445e409fe5054c7155851eb0560c3733f183df`. The checked-out `documentation/container-build.md` documented three local image builds and a local-image Compose startup. The optional local-map profile was not enabled.

## Documented Build Commands

Executed from the repository root exactly as the Linux/macOS/Git Bash block in `documentation/container-build.md` specifies:

```bash
export BUILDKIT_PROGRESS=plain
docker buildx build --load -t mytraillog:local .
docker buildx build --load -t mytraillog-brouter:local docker-brouter
docker buildx build --load -t mytraillog-location-search:local -f docker-location-search/Dockerfile .
```

Result:

```text
mytraillog-location-search:local eba5c0d3b628 193MB
mytraillog-brouter:local 71794ebcbc61 459MB
mytraillog:local 232a8aec5c79 1.21GB
```

## Documented Startup Command

Executed from the repository root exactly as documented:

```bash
MTL_APP_IMAGE=mytraillog:local \
MTL_BROUTER_IMAGE=mytraillog-brouter:local \
MTL_LOCATION_SEARCH_IMAGE=mytraillog-location-search:local \
MTL_IMAGE_PULL_POLICY=never \
docker compose up -d
```

Result: exit `0`. Compose created and started `db`, `app`, `brouter`, and `location-search`. The app returned HTTP `200` on `http://127.0.0.1:18080/mtl/` after 28 seconds.

Final status excerpt is in [final-compose-ps.txt](assets/final-compose-ps.txt).

## GPX Import

The delegated Home install data folder is `./data/gpx/`. Five public GPX files with `trk` / `trkseg` / `trkpt` sequences and trackpoint timestamps were downloaded and copied into that folder.

Command:

```bash
mkdir -p data/gpx
cp /root/mtl-container-build-test-2026-06-05/gpx-candidates/*.gpx data/gpx/
```

Source metadata and checksums are in [gpx-sample-metadata.txt](assets/gpx-sample-metadata.txt).

Import result after 14 seconds:

```text
gps_track_count=5
gps_track_v_count=5
indexed_file_count=5
indexed_status=COMPLETED_WITH_SUCCESS:5
load_status=SUCCESS:5
```

Imported track details are in [imported-tracks.txt](assets/imported-tracks.txt). A short app log excerpt is in [import-sync-app-lines.txt](assets/import-sync-app-lines.txt).

Note: `Mojstrovka.gpx` is a valid timestamped GPX track file, but MTL Explorer's outlier filter retained only one point and produced no map geometry for that sample. It still imported with `SUCCESS`; the later GUI map/stat count excludes it.

## Deletion Sync

Deleted one imported GPX file from the documented GPX folder:

```bash
rm data/gpx/around-visnjan-with-car.gpx
```

Result after 9 seconds:

```text
gps_track_count=4
gps_track_v_count=4
deleted_file_tracks=0
deleted_indexed_files=1
deleted_index_status=REMOVED
indexed_status=COMPLETED_WITH_SUCCESS:4
indexed_status=REMOVED:1
```

The deleted track row disappeared from `gps_track_v`. The `indexed_file` row remained as a `REMOVED` tombstone, which is useful state/audit evidence and did not keep the track counted or visible.

Post-deletion track details are in [post-delete-tracks.txt](assets/post-delete-tracks.txt). A short deletion log excerpt is in [delete-sync-app-lines.txt](assets/delete-sync-app-lines.txt).

## GUI Verification

Opened `http://167.233.16.201:18080/mtl/`, logged in with the documented default credentials `mtl` / `change-me`, and inspected the Stats GUI only.

Observed GUI signals:

```text
URL: http://167.233.16.201:18080/mtl/stats
Visible map counter present: true ("3 / 3 Tracks")
Stats track count present: true ("3 Tracks")
Deleted track name visible: false ("2020-12-18 07:24:29")
Deleted filename visible: false ("around-visnjan-with-car.gpx")
```

The remaining visible tracks were `Current Track: 02 AUG 2020 08:50`, `8/22/2014 18:48`, and the Cerknicko active-log track. GUI evidence is in [gui-verification.txt](assets/gui-verification.txt).

## Final Verification

Commands and results:

```bash
docker compose ps
curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/mtl/
curl -sS -o /dev/null -w '%{http_code}' http://167.233.16.201:18080/mtl/
curl -sS http://127.0.0.1:18083/health
docker compose exec -T brouter python3 -c '... GET http://127.0.0.1:17778/status ...'
```

Results:

```text
APP_LOCAL_STATUS=200
APP_PUBLIC_STATUS=200
LOCATION_SEARCH_HEALTH=ok
BRouter status: available=true, brouterRunning=true, version=1.7.9
gps_track_v_count=4
deleted_file_tracks=0
```

Final Docker disk summary is in [final-docker-system-df.txt](assets/final-docker-system-df.txt), and final disk usage is in [final-disk.txt](assets/final-disk.txt).

## Issues And Observations

| Severity | Observation | Impact |
|---|---|---|
| Info | Server forced a root password change before SSH automation could run. | Access setup only; MTL flow not started yet. |
| Info | Docker Engine, Buildx, Compose plugin, and Git were absent on the fresh server. | Installed prerequisites separately before the documented flow. |
| Info | RAM baseline was `3.7Gi` available on a host documented as needing about 4 GB minimum. | Build and stack still passed. |
| Info | Location-search sidecar had no GeoNames SQLite database. | Expected unless the optional DB is built or mounted; container health was `ok`, and container-build docs call this out. |
| Info | `Mojstrovka.gpx` imported with `SUCCESS` but was reduced to one point by outlier filtering. | Not used for deletion evidence; explains why GUI visible count was 3 while post-delete DB row count was 4. |
| Info | Final error grep includes two failed tester SQL inspection queries and one normal Liquibase startup probe for `databasechangeloglock`. | Not caused by the documented MTL Explorer flow. See [final-error-lines.txt](assets/final-error-lines.txt). |

## Conclusion

The documented container-build flow passed end to end on `167.233.16.201`: local images built, Compose started with the documented local-image variables, the app served the documented URL, public timestamped GPX files synced from `./data/gpx/`, deletion sync removed the selected track from app track data, and the GUI no longer counted or displayed the deleted track.
