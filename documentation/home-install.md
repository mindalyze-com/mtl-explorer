# Home Install

For the simplest setup, start with the [README quick start](../README.md#quick-start).
This page covers home install details: settings, data folders, maps, updates,
and logs.

## Requirements

MTL Explorer needs more resources than a small web app because the stack runs
the app, PostGIS, and BRouter routing together. Local vector maps are optional.

- Docker with Docker Compose. On Windows, install Docker Desktop.
- Docker host for `linux/amd64` or `linux/arm64`.
- About 4 GB RAM minimum; 6-8 GB is more comfortable.
- Free local port `18080` for the app and `18083` for the location-search
  sidecar. Port `18081` is used only by the optional local map sidecar.
- Stable internet for hosted maps. A fully local map setup needs about
  200 GB free disk for the initial full-world map download.

Both Compose v2 (`docker compose`) and classic Compose (`docker-compose`) work;
the examples use Compose v2.

## Login and HTTPS

The default credentials are `mtl` / `change-me`. Always change them before
exposing your instance outside your home network.

To customize credentials, create a `.env` file next to your `docker-compose.yml`:

```env
MTL_USER_LOGIN=your-user
MTL_USER_PASSWORD=change-this-password
```

Restart the containers to apply:

```bash
docker compose up -d
```

The compose stack serves plain HTTP on port `18080`. Use a reverse proxy such
as Caddy, Traefik, or nginx to configure HTTPS.

## Data

Docker creates these folders next to `docker-compose.yml`:

```text
./data/gpx               GPX/FIT import folder
./data/media             geotagged photos and videos
./data/postgis           PostgreSQL database
./data/maps              local map tiles and map-server logs, only with local-maps profile
./data/location-search   optional mounted GeoNames SQLite search database
./data/brouter-segments  BRouter routing tiles
./data/logs              application logs
```

Copy GPX or FIT files into `./data/gpx/`; the server discovers them
automatically. Put geotagged photos and videos into `./data/media/`.

If you already keep tracks or media somewhere else, edit the `app.volumes`
section in `docker-compose.yml` and replace the left side of the bind mount:

```yaml
volumes:
  - /your/tracks:/app/gpx
  - /your/media:/app/media:ro
```

Keep `./data/postgis`, `./data/brouter-segments`, optional
`./data/location-search`, and, when local maps are enabled, `./data/maps`
across upgrades.

On Linux, if Docker created `./data/` as root:

```bash
sudo chown -R "$USER:$USER" data
```

## Maps

The default install uses the hosted PMTiles service through the Java backend
proxy. The browser calls only the local `/api/map-proxy/...` endpoint and does
not receive the hosted upstream URL or server identity metadata.

For fully self-hosted map tiles, enable the optional `local-maps` profile:

```bash
docker compose --profile local-maps up -d
```

The initial local map download is about 120-130 GB, resumes if interrupted, and
is stored in `./data/maps/`. Keep this folder across restarts and upgrades.

For the simpler hosted-map setup, leave the `local-maps` profile off.

## Location Search

Location search runs in the `location-search` sidecar with a GeoNames-derived
SQLite database. It covers populated places and selected terrain features such
as peaks, passes, ridges, mountains, and hills. It is not an all-OSM-POI search.

## Route planner

BRouter runs as part of the default compose stack. Routing segments are stored
in `./data/brouter-segments/` and should be kept across upgrades. If route
planning is unavailable, check the BRouter logs:

```bash
docker compose logs -f brouter
```

## Updates and logs

```bash
docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f app brouter
```

Return to the [README](../README.md) for the short install path, or use
[Container build](container-build.md) if you want to build local Docker images
from the source checkout.
