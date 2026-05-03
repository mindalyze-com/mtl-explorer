# Home Install

Prerequisite: Docker with Docker Compose. On Windows, install Docker Desktop.
Both Compose v2 (`docker compose`) and classic Compose (`docker-compose`) work;
the examples below use Compose v2.

## System requirements

MTL Explorer needs more resources than a small web app because the stack runs
the app, PostGIS, and BRouter routing together. Local vector maps are optional.

- Docker host for `linux/amd64` or `linux/arm64`.
- About 4 GB RAM minimum; about 6-8 GB is more comfortable.
- Disk needs are mostly your own GPX/media data and PostgreSQL. Add about
  200 GB free disk only if you enable the optional full-world local map sidecar.
- Stable internet for hosted maps. If `map-server` is enabled, the initial
  local map download is about 120-130 GB and resumes if interrupted.
- Free local port `18080` for the app. Port `18081` is used only when the
  optional local map sidecar profile is enabled.

## Start

macOS / Linux / WSL / Git Bash:

```bash
mkdir mtl-explorer
cd mtl-explorer
curl -fsSL -o docker-compose.yml https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
docker compose up -d
```

Windows PowerShell:

```powershell
mkdir mtl-explorer
cd mtl-explorer
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml" -OutFile "docker-compose.yml"
docker compose up -d
```

If your system uses classic Compose, run `docker-compose up -d` instead.

Open:

```text
http://localhost:18080/mtl/
```

Default login: `mtl` / `change-me`.

Change it before exposing the instance outside your own machine or home network.
The compose file reads `MTL_USER_LOGIN` and `MTL_USER_PASSWORD`, so the simplest
way is to create or edit `.env` next to `docker-compose.yml`:

```bash
printf 'MTL_USER_LOGIN=your-user\nMTL_USER_PASSWORD=change-this-password\n' > .env
docker compose up -d
```

Use a reverse proxy such as Caddy, Traefik, or nginx if you want HTTPS.

## Data

Docker creates these folders next to `docker-compose.yml`:

```text
./data/gpx               GPX/FIT import folder
./data/media             geotagged photos and videos
./data/postgis           PostgreSQL database
./data/maps              local map tiles and map-server logs, only with local-maps profile
./data/brouter-segments  BRouter routing tiles
./data/logs              application logs
```

Drop GPX/FIT files into `./data/gpx/` and media into `./data/media/`.

## Maps

The default install uses the hosted PMTiles service through the Java backend
proxy. The browser never receives the hosted upstream URL or the server-id
metadata used by that service.

The compose file also includes an optional `map-server` sidecar under the
`local-maps` profile. When it is running, MTL Explorer uses the local PMTiles
archive and can be offline-capable after the map download finishes:

```bash
docker compose --profile local-maps up -d
```

The first local map download can be large; keep `./data/maps/` if you do not
want it downloaded again.

## Commands

```bash
docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f app brouter
```

On Linux, if Docker created `./data/` as root:

```bash
sudo chown -R "$USER:$USER" data
```
