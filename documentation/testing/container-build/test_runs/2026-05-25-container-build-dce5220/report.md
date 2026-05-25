> **RESULT: PASS - documented local image build, compose startup, GPX import, and GPX deletion sync all completed**

**Goal**
Test the MTL Explorer container-build flow on server `178.105.217.18` using GitHub `main` as source and `documentation/container-build.md` from that checkout as the procedure source of truth. Screenshots were intentionally not captured.

**Environment**
GitHub source checkout:

```text
Repository: https://github.com/mindalyze-com/mtl-explorer
Branch: main
Commit: dce522049dbe72637f66fb6b97cb684f274b866b
Checkout path: /root/mtl-container-build-test-2026-05-25
Documentation read: documentation/container-build.md
```

Server baseline before Docker setup:

```text
OS: Debian GNU/Linux 13 (trixie), DEBIAN_VERSION_FULL=13.5
Kernel: Linux 6.12.88+deb13-cloud-amd64 x86_64
CPU: 2 vCPU, AMD EPYC-Genoa Processor
RAM: 3.7 GiB total, 3.4 GiB available, no swap
Disk /: 75G size, 988M used, 71G available, 2% used
SSH host key: ED25519 SHA256:E1KlcZtdOPV9TaV1t5IZ633GxtTu61spuMhwHKN0BJE
```

Server state after the run:

```text
RAM: 3.7 GiB total, 2.4 GiB available, no swap
Disk /: 75G size, 7.5G used, 65G available, 11% used
```

**Prerequisite Setup**
This setup is separate from the documented MTL Explorer flow.

Initial checks showed Docker Engine, Buildx, and Compose were absent:

```text
docker: command not found
docker_rc=127
buildx_rc=127
compose_rc=127
docker service: inactive
```

Installed missing Docker prerequisites from Docker's official Debian apt repository. Candidate versions before install:

```text
docker-ce: 5:29.5.2-1~debian.13~trixie
docker-ce-cli: 5:29.5.2-1~debian.13~trixie
containerd.io: 2.2.4-1~debian.13~trixie
docker-buildx-plugin: 0.34.0-1~debian.13~trixie
docker-compose-plugin: 5.1.4-1~debian.13~trixie
```

Commands executed for Docker setup:

```bash
apt-get update
apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
printf "deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian %s stable\n" "$arch" "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Installed Docker versions:

```text
Docker version 29.5.2, build 79eb04c
Buildx: github.com/docker/buildx v0.34.0 3e73561e39785683b31b05eeab1ef645be44ca42
Docker Compose version v5.1.4
Docker server: ServerVersion=29.5.2 Driver=overlayfs CgroupVersion=2
```

`git` was also missing, so it was installed separately as source-checkout setup:

```bash
apt-get install -y git
```

Installed Git version:

```text
git version 2.47.3
```

A temporary SSH key was used for the test harness and removed after testing.

**Documented Procedure**
The non-optional Linux/macOS/WSL/Git Bash procedure in `documentation/container-build.md` was:

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

The optional `local-maps` image was not tested because the document marks it optional and notes the profile downloads about 130 GB on first startup.

**Build And Run Results**

| Step | Exact command | Result |
| --- | --- | --- |
| App image | `docker buildx build --load -t mytraillog:local .` | PASS, `RC=0`, `2026-05-25T16:28:51Z` to `16:32:51Z` |
| BRouter image | `docker buildx build --load -t mytraillog-brouter:local docker-brouter` | PASS, `RC=0`, `2026-05-25T16:33:17Z` to `16:33:29Z` |
| Location search image | `docker buildx build --load -t mytraillog-location-search:local -f docker-location-search/Dockerfile .` | PASS, `RC=0`, `2026-05-25T16:33:38Z` to `16:33:45Z` |
| Compose startup | `MTL_APP_IMAGE=mytraillog:local MTL_BROUTER_IMAGE=mytraillog-brouter:local MTL_LOCATION_SEARCH_IMAGE=mytraillog-location-search:local MTL_IMAGE_PULL_POLICY=never docker compose up -d` | PASS, `RC=0`, `2026-05-25T16:33:57Z` to `16:34:17Z` |

Relevant build evidence:

```text
App image:
#16 [INFO] BUILD SUCCESS
#16 [INFO] Total time:  03:24 min
#32 naming to docker.io/library/mytraillog:local done
RC=0

BRouter:
#13 naming to docker.io/library/mytraillog-brouter:local done
RC=0

Location search:
#14 naming to docker.io/library/mytraillog-location-search:local done
RC=0

Compose:
Image postgis/postgis:18-3.6 Pulled
Container ...-brouter-1 Started
Container ...-db-1 Healthy
Container ...-location-search-1 Started
Container ...-app-1 Started
RC=0
```

Loaded images:

```text
mytraillog:local 8f386b4120b1 1.22GB
mytraillog-brouter:local dba90bfa44c6 459MB
mytraillog-location-search:local 040798012d63 193MB
postgis/postgis:18-3.6 750ce2898856 955MB
```

Container status:

```text
app               mytraillog:local                   Up, 0.0.0.0:18080->8080/tcp
db                postgis/postgis:18-3.6             Up (healthy)
brouter           mytraillog-brouter:local           Up
location-search   mytraillog-location-search:local   Up (healthy), 0.0.0.0:18083->8083/tcp
```

**URL Verification**
The documented app URL from README/Home Install is `http://localhost:18080/mtl/`.

```text
curl http://localhost:18080/mtl/
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8
<title>MTL Explorer</title>

curl http://178.105.217.18:18080/mtl/
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8
<title>MTL Explorer</title>
```

Unauthenticated API access was protected:

```text
curl http://localhost:18080/mtl/api/server-info
HTTP/1.1 401
{"error":"Unauthorized"}
```

The first URL probe during Spring startup returned `Recv failure: Connection reset by peer`; a retry after the app completed startup returned HTTP 200. This was not blocking.

**GPX Samples**
The documented GPX folder is `./data/gpx`, via `documentation/container-build.md` pointing to Home Install data folders.

Downloaded public timestamped GPX samples:

| Source URL | Destination | SHA-256 | Size | Trackpoints | Trackpoints with timestamps |
| --- | --- | --- | ---: | ---: | ---: |
| `https://raw.githubusercontent.com/cjoakim/ggps-py/main/data/dav_track_5k.gpx` | `data/gpx/public-dav-track-5k.gpx` | `8bb392a1e0d26d31b824c7149c9cb2bebad7066eaac3fd6a7bc986645b2ddea7` | 39915 | 245 | 245 |
| `https://raw.githubusercontent.com/cjoakim/ggps-py/main/data/activity_4564516081.gpx` | `data/gpx/public-activity-4564516081.gpx` | `1fbf0c8a4c56e0d06308c6f3c9a3ad369e37f8f7b94f427939fcfa93c838384d` | 807589 | 2177 | 2177 |
| `https://raw.githubusercontent.com/cjoakim/ggps-py/main/data/activity_607442311.gpx` | `data/gpx/public-activity-607442311.gpx` | `41972890b680420ec0e9170b01babb5bfdf26e1e4817cbade0e49ca4483f4f95` | 827931 | 2256 | 2256 |

Import log evidence:

```text
Live watcher detected CREATE for: public-dav-track-5k.gpx
Live watcher detected CREATE for: public-activity-4564516081.gpx
Live watcher detected CREATE for: public-activity-607442311.gpx
Reading of track id=100000 ... file=public-dav-track-5k.gpx did complete with status=SUCCESS
Reading of track id=100001 ... file=public-activity-4564516081.gpx did complete with status=SUCCESS
Reading of track id=100002 ... file=public-activity-607442311.gpx did complete with status=SUCCESS
```

Database evidence after import:

```text
tracks: 3
trackpoints: 4678

100000 public-dav-track-5k.gpx        SUCCESS 245
100001 public-activity-4564516081.gpx SUCCESS 2177
100002 public-activity-607442311.gpx  SUCCESS 2256
```

GUI evidence after import:

```text
Stats panel: 3 Tracks
Recent Activity included:
- Mecklenburg County Running
- Davidson Running
- Twin Cities Marathon 2014 - Chris Joakim
```

**Deletion Sync**
Deleted one GPX file from the documented folder:

```bash
rm data/gpx/public-dav-track-5k.gpx
```

Deletion log evidence:

```text
2026-05-25T16:39:12.228Z GPXStoreService:
Did delete gpsTrack and it's track data for given id=100000 file=/public-dav-track-5k.gpx
```

Database evidence after deletion:

```text
tracks: 2
trackpoints: 4433

100001 public-activity-4564516081.gpx SUCCESS 2177
100002 public-activity-607442311.gpx  SUCCESS 2256

indexed_file:
public-activity-4564516081.gpx COMPLETED_WITH_SUCCESS
public-activity-607442311.gpx  COMPLETED_WITH_SUCCESS
public-dav-track-5k.gpx        REMOVED
```

GUI evidence after refresh:

```text
Top map label: 2 Tracks
Stats panel: 2 Tracks
Recent Activity included:
- Mecklenburg County Running
- Twin Cities Marathon 2014 - Chris Joakim
Deleted track absent:
- Davidson Running was no longer present
```

**Issues**
Blocking issues: none.

Non-blocking observations:

- The server has 3.7 GiB RAM and no swap, slightly below the Home Install "about 4 GB RAM minimum" guidance. The build and runtime still completed.
- The app image build logged npm `EBADENGINE` warnings for Babel `8.0.0-rc.5` packages requiring Node `^22.18.0 || >=24.11.0`, while the build image used Node `v20.20.2`. The build completed successfully.
- Location search reported `GeoNames search database is missing`; `documentation/container-build.md` explicitly says useful local location search requires building or mounting `docker-location-search/geonames-search/build/geonames-search.sqlite`. This was not required for the documented container-build flow used here, and the sidecar container remained healthy.

**Conclusion**
The documented MTL Explorer container-build flow passed end to end on the requested server. Local images built from GitHub `main`, Compose started the stack using the local image tags, the documented app URL returned HTTP 200, three timestamped GPX files synced successfully, and deleting one GPX file removed the corresponding track from both the database and the GUI count/list.
