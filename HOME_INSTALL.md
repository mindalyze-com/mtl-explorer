# Home Install

Prerequisite: Docker with Docker Compose.

## 1. Prepare folders

Choose folders on your Docker host. Example:

```bash
mkdir -p /srv/mtl/{postgis,logs,config,maps,brouter-segments}
```

Create `/srv/mtl/config/application.yml` and change the login:

```yaml
mtl:
  user:
    login: "your-user"
    password: "change-this-password"
```

## 2. Configure mounts

Use the normal `docker-compose.yml`, not `docker-compose-demo.yml`.
Keep its ports (`18080` for MTL, `18081` for maps) and do not set
`DEMO_MODE`. Replace only the host paths on the left side of each volume mount:

```yaml
services:
  db:
    volumes:
      - /srv/mtl/postgis:/var/lib/postgresql/18/docker

  app:
    volumes:
      - /path/to/your/gpx:/app/gpx:ro
      - /srv/mtl/logs:/app/logs
      - /srv/mtl/config/application.yml:/app/config/application.yml:ro
      - /path/to/your/photos:/app/media:ro

  map-server:
    environment:
      MAP_DOWNLOAD_URL: latest
    volumes:
      - /srv/mtl/maps:/data
```

`/path/to/your/gpx` is your folder with GPX files. It can be read-only
(`:ro`) if MTL only imports existing files from it.

`/path/to/your/photos` is your photo/video folder. MTL indexes geotagged media
from this tree; mounting it read-only is recommended.

The map server downloads OSM/Protomaps data on first start because
`MAP_DOWNLOAD_URL: latest` is set. This can be very large and may take a long
time. Keep `/srv/mtl/maps` if you do not want it downloaded again.

## 3. Start

```bash
docker compose up -d
docker compose logs -f app map-server
```

Open:

```text
http://<host>:18080/mtl/
```

The `/mtl/` path is required. On the same machine, use
`http://localhost:18080/mtl/`.

Optional route planner:

```bash
MTL_PLANNER_ENABLED=true docker compose --profile planner up -d
```
