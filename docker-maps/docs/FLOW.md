# Map Server Flow

> [!NOTE]
> This is a developer-focused design document. For user-facing setup instructions and configuring local maps, refer to the **[Home Install Guide - Maps](../../documentation/home-install.md#maps)**.

## Design Principles

1. **Hosted by default** - normal app and demo deployments use the hosted
   PMTiles service through the Java backend proxy.
2. **Local sidecar is optional** - Docker Compose keeps `map-server` visible
   under the `local-maps` profile. It is not started by default.
3. **No baked map archives** - the `mytraillog-maps` image contains nginx,
   the PMTiles CLI, and the orchestrator only. Map data lives on the `/data`
   volume or is prepared at runtime.
4. **Versioned tile files** - every tile file is named by its Protomaps build
   date: `YYYYMMDD.pmtiles`.
5. **Stable symlinks** - `planet.pmtiles` and `world-lowzoom.pmtiles` are
   symbolic links pointing to the latest complete versioned files.
6. **Sentinel files** - a `.done` file next to a versioned tile means it is
   complete. Symlinks are created only after the sentinel exists.
7. **Resume safety** - on restart, the orchestrator resumes an incomplete
   `YYYYMMDD.pmtiles` version instead of switching to a newer build.
8. **Java proxy owns upstream choice** - the browser calls
   `/mtl/api/map-proxy/prod/...` for both hosted and local PMTiles. The backend
   chooses local sidecar when healthy, otherwise hosted.

---

## Build Time

`docker-build-deploy.py` builds and pushes the map image like any other image.
It no longer probes Protomaps builds and no longer passes map build arguments.

The Dockerfile builds one lightweight runtime image:

- Alpine
- nginx
- python3
- `pmtiles` CLI
- orchestrator scripts
- entrypoint

No `/data-prebuilt` directory is copied into the image.

---

## Container Startup

### `entrypoint.sh`

The entrypoint directly execs:

```bash
python3 /app/scripts/orchestrator.py
```

### `orchestrator.py`

1. Sets `os.umask(0)` so created files are manageable from mounted volumes.
2. Installs signal handlers for `SIGTERM` and `SIGINT`.
3. Starts nginx on port 8081. Docker health checks can pass immediately.
4. Starts an internal kickoff HTTP server on `127.0.0.1:8082`.
5. Writes `status=waiting-kickoff`.
6. Starts a background worker thread that waits for kickoff requests.
7. Keeps nginx in the foreground process group.

---

## Backend Startup

`MapServerKickoffService` sends a kickoff only when:

- `mtl.map-server.tile-mode` is `local`
- a tile upstream URL is configured
- the resolver can reach a healthy local sidecar

The application-managed local map scope is always `prod`, including demo mode.
Demo deployments therefore do not require a separate preloaded or area-limited
map archive.

If no local sidecar is healthy, the backend uses the hosted PMTiles upstream
without logging a failed kickoff.

---

## Background Worker

The worker receives a scope from `/kickoff/{scope}` and operates on:

```text
/data/{scope}
```

For normal app use, the scope is `prod`.

### Step A - Already ready

The worker checks whether `planet.pmtiles` points to a real versioned file with
a matching `.done` sentinel. If ready, it:

- checks file permissions
- ensures the low-zoom archive exists
- refreshes symlinks
- writes `status=ready`

### Step B - Incomplete download

If a `YYYYMMDD.pmtiles` file exists without a `.done` sentinel, the worker
resumes that exact version using the stored `.url` sentinel or reconstructs the
Protomaps URL from the date.

### Step C - New runtime preparation

If no ready or incomplete archive exists and `MAP_DOWNLOAD_URL` is configured,
the worker resolves the URL, derives the build date, and prepares the archive.

`MAP_DOWNLOAD_URL=latest` probes recent Protomaps daily builds through
`PROTOMAPS_BUILDS_BASE_URL`, defaulting to `https://build.protomaps.com`.

### Step D - Nothing to serve

If there is no archive and no `MAP_DOWNLOAD_URL`, the worker writes an error
status. The Java backend can still use hosted PMTiles when the local sidecar is
not selected.

---

## Download Modes

### Full planet mode

When `MAP_AREA_BBOX` is empty:

- `wget --continue` downloads the full PMTiles archive
- progress is written to `/tmp/map-status.json`
- `YYYYMMDD.pmtiles.done` is written on success
- low-zoom extraction starts after the full archive is available

### Area-only mode

When `MAP_AREA_BBOX` is set:

- `pmtiles extract <url> YYYYMMDD.pmtiles --bbox=... --maxzoom=15`
- CDN range requests fetch only the requested area
- low-zoom extraction can read from the remote source URL

This mode remains available for custom local deployments, but it is no longer
used for demo preloaded maps.

---

## Tile Serving

The browser receives archive URLs like:

```text
/mtl/api/map-proxy/prod/planet.pmtiles?mtl-map-source=public&mtl-map-archive=public-default
```

or, when the local sidecar is healthy:

```text
/mtl/api/map-proxy/prod/planet.pmtiles?mtl-map-source=local&mtl-map-archive=20260404
```

`MapTileProxyController`:

- validates scope and filename
- rejects stale `mtl-map-source` / `mtl-map-archive` cache identities
- appends private `mtl-version` and `mtl-server-id` parameters upstream
- streams HTTP Range responses back to the browser

nginx rejects PMTiles requests that do not include those private upstream
parameters.

---

## Volume Layout

```text
/data/
└── prod/
    ├── 20260404.pmtiles
    ├── 20260404.pmtiles.done
    ├── 20260404.pmtiles.url
    ├── 20260404-lowzoom.pmtiles
    ├── 20260404-lowzoom.pmtiles.done
    ├── planet.pmtiles -> 20260404.pmtiles
    └── world-lowzoom.pmtiles -> 20260404-lowzoom.pmtiles
```

Old versions are not auto-deleted to avoid accidental data loss.

---

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MAP_DOWNLOAD_URL` | No | empty | `latest` to auto-resolve the most recent Protomaps build, or a direct URL. If empty, the orchestrator serves only existing tiles. |
| `PROTOMAPS_BUILDS_BASE_URL` | No | `https://build.protomaps.com` | Daily-build base URL used when `MAP_DOWNLOAD_URL=latest`. |
| `MAP_AREA_BBOX` | No | empty | `west,south,east,north` for area-only local extraction instead of full planet download. |
| `LOWZOOM_MAXZOOM` | No | `6` | Maximum zoom level for the offline low-zoom extract. |
| `PORT` | No | `8081` | nginx listen port. |

---

## Internal Kickoff API

| Method | Path | Effect |
|---|---|---|
| `PUT /kickoff/prod` | Checks existing tiles, resumes incomplete work, or starts runtime preparation. |
| `DELETE /kickoff/prod` | Removes symlinks, sentinels, and tile files in `/data/prod`, then reprocesses. |

`PUT` and `POST` are both accepted.
