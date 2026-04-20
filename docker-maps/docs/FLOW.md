# Map Server — Full Flow

## Design Principles

1. **Versioned tile files** — every tile file is named by its Protomaps build date: `YYYYMMDD.pmtiles`.
2. **Symlinks as the stable name** — `planet.pmtiles` and `world-lowzoom.pmtiles` are always symbolic links pointing to the latest *complete* versioned file. nginx follows symlinks natively.
3. **Sentinel files** — a `.done` file next to a versioned tile means it is complete. Symlinks are only created after the sentinel exists.
4. **Resume safety** — on restart, the orchestrator finds any `YYYYMMDD.pmtiles` without a `.done` and resumes *that* version (not a newer one).
5. **Two independent scopes** — `/data/demo/` (seeded from pre-built tiles) and `/data/prod/` (downloaded at runtime). Each scope has its own versioned files and symlinks. They survive independently across restarts and mode changes.
6. **Scope-prefixed URLs** — the client receives a scope-prefixed `tileBaseUrl` (e.g. `/mtl/api/map-proxy/demo`). The scope in the URL path provides natural browser cache isolation between demo and prod — no `tileCacheMarker` or query-string hacks needed.
7. **Transparent proxy** — the Java `MapTileProxyController` accepts `/{scope}/{filename}` and forwards to `http://map-server:8081/{scope}/{filename}` without interpreting the scope.
8. **Stateless orchestrator** — the orchestrator does not decide scope at startup. It waits for a kickoff request from Java that carries the scope (`PUT /kickoff/demo` or `PUT /kickoff/prod`). All functions receive the scope directory as an explicit parameter.

---

## Build Time (developer machine)

### 1. `docker-build-deploy.sh`
- Probes `build.protomaps.com` via HTTP HEAD, trying today, yesterday, … up to 60 days back.
- Passes the resolved URL as `--build-arg PROTOMAPS_URL=https://build.protomaps.com/YYYYMMDD.pmtiles`.

### 2. Dockerfile multi-stage build
- **Stage 1 (`base`):** Alpine + nginx + python3 + `pmtiles` CLI + orchestrator scripts + entrypoint.
- **Stage 2 (`baker`):** Only runs when `BAKE_DEMO_AREA` build-arg is provided (defaults to the Iberian Peninsula bbox `-13.0,39.3,-4.5,42.6` for Porto demo data).
  - Parses the build date from `PROTOMAPS_URL` (e.g. `20260404`).
  - Extracts the configured area → `/data-prebuilt/demo/20260404.pmtiles` + `.done` (`--maxzoom=15`).
  - Extracts global low-zoom → `/data-prebuilt/demo/20260404-lowzoom.pmtiles` + `.done` (controlled by `BAKE_LOWZOOM_MAXZOOM`, default **5**).
  - Creates symlinks: `planet.pmtiles → 20260404.pmtiles`, `world-lowzoom.pmtiles → 20260404-lowzoom.pmtiles`.
  - Note: the baker uses `BAKE_LOWZOOM_MAXZOOM=5`; the orchestrator runtime uses `LOWZOOM_MAXZOOM=6` (env var). These are intentionally separate — the baked demo low-zoom is coarser to keep the image small.
- **Stage 3 (`final`):** Copies `/data-prebuilt/` from baker. This directory is **outside** the `/data` volume mount point, so it survives volume mounts.

---

## Container Startup

### 3. `entrypoint.sh`
- Thin wrapper — directly `exec`s `python3 /app/scripts/orchestrator.py`.
- Seeding of pre-built tiles is handled by the orchestrator on each kickoff (not in the entrypoint).

### 4. `orchestrator.py` — `main()`
1. Sets `os.umask(0)` so all created files/directories and child processes use open permissions.
2. Installs signal handlers for `SIGTERM` / `SIGINT` (forwards termination to child processes).
3. **Starts nginx** on port 8081 in the foreground process group (Docker HEALTHCHECK hits `GET /health` immediately → container is **healthy**).
4. **Starts kickoff HTTP server** on `127.0.0.1:8082` (internal only — not exposed to Docker network).
5. Writes `status=waiting-kickoff` — orchestrator is idle until Java sends a kickoff.
6. **Starts background worker thread** — blocks on `_kickoff_queue.get()`.
7. Main thread calls `nginx.wait()` — blocks forever, forwarding Docker signals via the installed handlers.

---

## Background Worker Loop

The worker blocks on a queue. Each kickoff message carries a scope string (`"demo"` or `"prod"`). On receiving a kickoff:

1. Computes `scope_dir = /data/{scope}`, creates it if missing, sets permissions to 777.
2. Calls `seed_prebuilt(scope)` — copies tiles from `/data-prebuilt/{scope}/` into `/data/{scope}/` using `cp -a` (preserves symlinks). No-op if `planet.pmtiles` symlink already exists in the destination.

### Step A — Already ready?
- Checks if `planet.pmtiles` in `scope_dir` is a symlink pointing to a real file with a matching `.done` sentinel.
- If yes:
  - Checks file permissions via `check_pmtiles_permissions()` (warns if not world-readable — nginx runs as `nginx` user).
  - **Always calls `extract_lowzoom()`** — this is a no-op if `YYYYMMDD-lowzoom.pmtiles.done` already exists, but ensures the low-zoom file is (re-)created if it was missing or interrupted (e.g. on a re-kickoff after a crash mid-extraction).
  - In `MAP_AREA_BBOX` mode, reads the `.url` sentinel to pass the remote URL as the low-zoom source; in full-planet mode, the low-zoom source is the local file.
  - Calls `create_symlinks()` to refresh/create the `world-lowzoom.pmtiles` symlink if the low-zoom file just became available.
  - Writes `status=ready`, logs `Tile server is READY`.
  - Goes back to queue.

### Step B — Incomplete download to resume?
- Scans `scope_dir` for any `YYYYMMDD.pmtiles` file that does **not** have a `.done` sentinel.
- If found → reads the `.url` sentinel (or reconstructs the URL from the date string) → resumes the download/extract → writes `.done` → low-zoom extraction → creates symlinks → `status=ready` → go back to queue.

### Step C — Need to download?
- Only reached if `MAP_DOWNLOAD_URL` env var is set (e.g. `latest` or a direct URL).
- Resolves the URL, derives the build date `YYYYMMDD` from it, downloads/extracts → `.done` → low-zoom extraction → symlinks → `status=ready` → go back to queue.

### Step D — Nothing to do
- No symlink, no incomplete file, no download URL → error status: `"No tiles found and no MAP_DOWNLOAD_URL configured"`.

---

## Java Backend Startup

### 5. `MapServerKickoffService` — `@EventListener(ApplicationReadyEvent.class)`
- Checks `tile-mode == "local"` (skip if `remote`) and `tile-upstream-url` is not blank.
- Determines the scope from `isDemoMode()`: demo → `"demo"`, prod → `"prod"`.
- Builds kickoff URL: `http://map-server:8081/kickoff/{scope}`.
- Sends **`PUT`** to that URL. If unreachable → logs warning, not fatal.

### 6. nginx routing for non-GET requests
- The `location ~* \.pmtiles$` block and the `location /` fallback block both have `limit_except GET HEAD OPTIONS { proxy_pass http://127.0.0.1:8082; }`.
- `PUT /kickoff/{scope}` hits `location /` (no `.pmtiles` suffix) → nginx proxies it to the internal kickoff server on port 8082.
- `DELETE /kickoff/{scope}` is similarly proxied.

### 7. `KickoffHandler._handle_kickoff()`
- Parses scope from the URL path: `/kickoff/demo` → `"demo"`.
- Puts the scope string on `_kickoff_queue` → **wakes the blocked worker thread**.
- Accepts `GET`, `POST`, `PUT` — all trigger a kickoff identically.
- Returns `200 {"status": "ok"}`.

### 7b. `KickoffHandler.do_DELETE()`
- Parses scope from the URL path: `/kickoff/demo` → `"demo"`.
- Removes all symlinks (`planet.pmtiles`, `world-lowzoom.pmtiles`) in `scope_dir`.
- Removes all non-symlink files matching `*.pmtiles*` (versioned tiles, `.done` sentinels, `.url` sentinels).
- Puts the scope string on `_kickoff_queue` → re-triggers the worker to start fresh.
- Returns `200 {"status": "ok"}`.

---

## Download / Extract (worker continues from Step C)

### 8. URL resolution (`MAP_DOWNLOAD_URL=latest`)
- Scans for any incomplete `YYYYMMDD.pmtiles` file (without `.done`) in `scope_dir`:
  - **Found:** Reads its `.url` sentinel to get the URL → resumes that specific version.
  - **Not found:** Calls `resolve_latest_url()` from `common.py` — HEAD probes `{PROTOMAPS_BUILDS_BASE_URL}/{YYYYMMDD}.pmtiles` for today, yesterday, … up to 60 days back. The base URL defaults to `https://build.protomaps.com` and can be overridden at runtime via `PROTOMAPS_BUILDS_BASE_URL` env var without rebuilding.
- Derives the date string from the URL: `https://build.protomaps.com/20260404.pmtiles` → `20260404`.
- Saves the resolved URL to `YYYYMMDD.pmtiles.url` sentinel (resume safety across restarts).

### 9a. Area-only mode (`MAP_AREA_BBOX` set)
- Runs `pmtiles extract <url> YYYYMMDD.pmtiles --bbox=... --maxzoom=15` into `scope_dir`.
- Uses CDN Range requests — downloads only ~100–300 MB for a city-level bbox.
- Writes `YYYYMMDD.pmtiles.done` sentinel.

### 9b. Full planet mode (no `MAP_AREA_BBOX`)
- Runs **`wget --continue`** → resumes from partial file if container was killed mid-download.
- Background monitor thread polls file size every 10 s and writes download progress to `/tmp/map-status.json`.
- If the `Content-Length` header is absent (some CDNs), falls back to a `Range: bytes=0-0` GET to extract the total from the `Content-Range` response header.
- On success: writes `YYYYMMDD.pmtiles.done` sentinel.

### 10. Low-zoom extraction
- Runs `pmtiles extract --maxzoom={LOWZOOM_MAXZOOM}` (default 6, configurable via env var) to create `YYYYMMDD-lowzoom.pmtiles` in `scope_dir`.
- **Source depends on mode:**
  - **`MAP_AREA_BBOX` set** — extracts from the **remote CDN URL** via HTTP Range requests. This means the low-zoom file covers the full global extent (no bbox, just zoom cap), and is produced immediately without a local planet file.
  - **Full planet mode** — extracts from the **local versioned file** (`YYYYMMDD.pmtiles`). The low-zoom extraction therefore only starts **after the full ~120 GB planet download completes**. There is no world-lowzoom.pmtiles during the download phase — this is expected behaviour.
- Writes `YYYYMMDD-lowzoom.pmtiles.done` sentinel.
- If already complete (`.done` exists and file present), `extract_lowzoom()` is a no-op and logs "already complete — skipping".

### 11. Symlink creation
- Atomically creates/replaces symlinks in `scope_dir`:
  - `planet.pmtiles → YYYYMMDD.pmtiles`
  - `world-lowzoom.pmtiles → YYYYMMDD-lowzoom.pmtiles` (only if low-zoom `.done` exists)
- Uses the atomic pattern: write a temp `.tmp` symlink, then `os.rename()` over the old one.

### 12. Ready state
- Writes status `{"phase": "ready", "ready": true, "download_pct": 100, ...}`.
- Logs `Tile server is READY and serving requests`.
- Worker goes back to `_kickoff_queue.get()` — waits for next kickoff or DELETE.

---

## Tile Serving (steady state)

### 13. Browser requests a tile
- Client calls `GET /mtl/api/map-proxy/demo/planet.pmtiles` (with `Range` header).
  The `tileBaseUrl` already includes the scope (e.g. `/mtl/api/map-proxy/demo`).
- **`MapTileProxyController`** in Java:
  - Validates scope (`demo` or `prod`) and filename (no path traversal).
  - Proxies to `http://map-server:8081/demo/planet.pmtiles`.
  - Transparent forwarder — no `isDemoMode()` logic in the tile-serving chain.
- **nginx** on the map-server receives `GET /demo/planet.pmtiles`:
  - The `location ~* \.pmtiles$` block matches → `root /data; try_files $uri =404;` resolves the symlink `/data/demo/planet.pmtiles` → `/data/demo/YYYYMMDD.pmtiles`.
  - nginx handles `Range` headers natively → returns `206 Partial Content` with the requested byte range.
  - Response headers: `Cache-Control: public, max-age=2678400, immutable`, and full CORS headers including `Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges, ETag`.

### 14. Browser cache isolation
- Demo tiles are fetched from `/mtl/api/map-proxy/demo/planet.pmtiles`.
- Prod tiles are fetched from `/mtl/api/map-proxy/prod/planet.pmtiles`.
- These are different URL paths → separate browser cache entries. No `tileCacheMarker` or query-string hacks needed.

### 15. Status polling
- Java's `MapServerStatusService` polls `GET http://map-server:8081/status` with a 10 s TTL cache.
- nginx serves `/tmp/map-status.json` directly (written atomically by the orchestrator via `.tmp` rename).
- Status JSON fields: `phase` (string), `ready` (bool), `download_pct` (0–100), `download_bytes`, `download_total`, `message`.
- Known phases: `starting`, `waiting-kickoff`, `downloading`, `area-extract`, `extracting`, `ready`, `error`.
- The client can see download progress, errors, or ready state.

---

## File layout on the volume

`/data/` = host's volume mount (e.g. `/volume1/raspberry/MyTrailLog/maps/`)

```
/data/
├── demo/                               ← seeded from /data-prebuilt/demo/ on first kickoff
│   ├── 20260404.pmtiles                ← actual tile file (baked at build time)
│   ├── 20260404.pmtiles.done           ← completion sentinel
│   ├── 20260404-lowzoom.pmtiles        ← low-zoom extract
│   ├── 20260404-lowzoom.pmtiles.done   ← completion sentinel
│   ├── planet.pmtiles                  ← symlink → 20260404.pmtiles
│   └── world-lowzoom.pmtiles           ← symlink → 20260404-lowzoom.pmtiles
└── prod/                               ← downloaded production tiles
    ├── 20260404.pmtiles                ← actual tile file (downloaded at runtime)
    ├── 20260404.pmtiles.done           ← completion sentinel
    ├── 20260404.pmtiles.url            ← resolved download URL (resume safety)
    ├── 20260404-lowzoom.pmtiles        ← low-zoom extract
    ├── 20260404-lowzoom.pmtiles.done   ← completion sentinel
    ├── planet.pmtiles                  ← symlink → 20260404.pmtiles
    └── world-lowzoom.pmtiles           ← symlink → 20260404-lowzoom.pmtiles
```

Pre-built tiles baked into the image (not on the volume):

```
/data-prebuilt/
└── demo/                               ← baked at Docker build time (baker stage)
    ├── 20260404.pmtiles
    ├── 20260404.pmtiles.done
    ├── 20260404-lowzoom.pmtiles
    ├── 20260404-lowzoom.pmtiles.done
    ├── planet.pmtiles                  ← symlink → 20260404.pmtiles
    └── world-lowzoom.pmtiles           ← symlink → 20260404-lowzoom.pmtiles
```

After a version upgrade in prod (e.g. new Protomaps build `20260501`):

```
/data/prod/
├── 20260404.pmtiles                ← old version (not auto-deleted)
├── 20260404.pmtiles.done
├── 20260404-lowzoom.pmtiles
├── 20260404-lowzoom.pmtiles.done
├── 20260501.pmtiles                ← new version
├── 20260501.pmtiles.done
├── 20260501-lowzoom.pmtiles
├── 20260501-lowzoom.pmtiles.done
├── planet.pmtiles                  ← symlink → 20260501.pmtiles (updated)
└── world-lowzoom.pmtiles           ← symlink → 20260501-lowzoom.pmtiles (updated)
```

Old versions are **not** auto-deleted to avoid accidental data loss. The user can clean up manually.
Demo and prod scopes are fully independent — switching demo-mode on/off does not affect the other scope's tiles.

---

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MAP_DOWNLOAD_URL` | No | *(empty)* | `latest` to auto-resolve the most recent Protomaps build, or a direct URL. If empty, the orchestrator serves only pre-existing/seeded tiles. |
| `MAP_AREA_BBOX` | No | *(empty)* | `"west,south,east,north"` — extract only a geographic area (~100–300 MB) instead of the full planet (~120 GB). |
| `LOWZOOM_MAXZOOM` | No | `6` | Maximum zoom level for the offline low-zoom extract. |
| `PORT` | No | `8081` | nginx listen port. |

`MAP_FILENAME` has been **removed** — filenames are always derived from the Protomaps build date.

---

## Key safety mechanisms

| Concern | Solution |
|---|---|
| Protomaps builds are irregular (gaps up to 4 weeks) | `resolve_latest_url()` probes up to 60 days back |
| Resume after kill downloads wrong day's file | Versioned filenames: incomplete `YYYYMMDD.pmtiles` without `.done` → resume that exact version |
| Volume mount shadows baked demo tiles | Baked into `/data-prebuilt/demo/`, seeded into `/data/{scope}/` by orchestrator on kickoff |
| nginx can't read files (different UID) | `check_pmtiles_permissions()` inspects mode bits and logs fix commands |
| Container starts before Java is ready | nginx is healthy immediately; worker blocks on kickoff queue |
| Java starts before map-server container | `MapServerKickoffService` catches connection errors, logs warning |
| Symlink replacement must be atomic | Write temp symlink, then `os.rename()` over the old one |
| Browser cache stale after version switch | Symlink target change alters ETag/Last-Modified → browser re-fetches |
| Demo/prod cache collision | Scope in URL path (`/demo/` vs `/prod/`) → separate browser cache entries |

---

## Kickoff and deletion API (internal, port 8082 via nginx proxy)

| Method | Path | Effect |
|---|---|---|
| `PUT /kickoff/demo` | Seeds pre-built demo tiles (if needed), then checks/downloads/ready |
| `PUT /kickoff/prod` | Seeds pre-built prod tiles (if any), then checks/downloads/ready |
| `DELETE /kickoff/demo` | Removes symlinks + sentinels + tile files in `/data/demo/` → re-processes on next kickoff |
| `DELETE /kickoff/prod` | Removes symlinks + sentinels + tile files in `/data/prod/` → re-processes on next kickoff |

Both `PUT` and `POST` are accepted.
