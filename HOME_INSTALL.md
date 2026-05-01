# Home Install

Prerequisite: Docker with Docker Compose. On Windows, install Docker Desktop.

## 1. Download and start

Create an empty folder for MTL Explorer, then download the published home
compose file.

macOS / Linux / WSL / Git Bash:

```bash
mkdir mtl-explorer
cd mtl-explorer
wget https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
docker compose up -d
```

Windows PowerShell:

```powershell
mkdir mtl-explorer
cd mtl-explorer
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml" -OutFile "docker-compose.yml"
docker compose up -d
```

Open:

```text
http://localhost:18080/mtl/
```

The `/mtl/` path is required. On another device in your home network, replace
`localhost` with the Docker host name or IP address.

The default login is:

```text
user: mtl
password: change-me
```

Change the login before exposing the instance outside your own machine or home
network. Put the values in a `.env` file next to `docker-compose.yml` so they
are reused on later `docker compose` commands:

```bash
printf 'MTL_USER_LOGIN=your-user\nMTL_USER_PASSWORD=change-this-password\n' > .env
docker compose up -d
```

## 2. Where data goes

The compose file uses relative bind mounts:

```text
./data/postgis           -> PostgreSQL database
./data/gpx               -> GPX/FIT import folder
./data/media             -> geotagged photos and videos
./data/logs              -> application logs
./data/maps              -> downloaded map tiles
./data/brouter-segments  -> BRouter routing tiles
```

You do not need to create those folders manually. Docker Compose creates them
on first start. Add GPX files to `./data/gpx/` and media files to
`./data/media/`; the app watches the GPX folder and periodically scans media.

The map server downloads OSM/Protomaps data on first start because
`MAP_DOWNLOAD_URL: latest` is set in the compose file. This can be very large
and may take a long time. Keep `./data/maps/` if you do not want it downloaded
again.

The MTL Explorer app, map, and BRouter images use the `latest` tag. In this
compose file, `latest` means the currently published stable MTL Explorer release.

## 3. Use GPX or media folders somewhere else

Edit only the left side of a volume mount. Keep the container path on the
right side unchanged.

For example, if your GPX archive is already at `/mnt/tracks/gpx`:

```yaml
services:
  app:
    volumes:
      - /mnt/tracks/gpx:/app/gpx:ro
      - ./data/media:/app/media:ro
      - ./data/logs:/app/logs
```

If your photo archive is somewhere else too:

```yaml
services:
  app:
    volumes:
      - /mnt/tracks/gpx:/app/gpx:ro
      - /mnt/photos:/app/media:ro
      - ./data/logs:/app/logs
```

Use `:ro` for existing archives that MTL Explorer should only read. Remove
`:ro` from the GPX mount if Garmin sync should write downloaded tracks into
that folder.

## 4. Route planner

The route planner is enabled in the home install. The BRouter sidecar starts
with the normal stack, and its route segment cache is stored in
`./data/brouter-segments/`.

## 5. Useful commands

Update to the current stable MTL Explorer images:

```bash
docker compose pull
docker compose up -d
```

Inspect the running stack:

```bash
docker compose ps
docker compose logs -f app map-server
```

On Linux, if Docker created `./data/` as root and your user cannot copy files
into it, fix ownership from the install folder:

```bash
sudo chown -R "$USER:$USER" data
```
