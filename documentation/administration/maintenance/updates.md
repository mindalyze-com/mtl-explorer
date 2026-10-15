# Updates

**Settings → Updates** shows new releases, their release notes, and how to
update the app and the services you run yourself.

## Update

1. Read the [release notes](../../release-notes/README.md) for the new version.
   Some releases need extra steps.
2. [Back up](backup-and-restore.md) your installation.
3. Run `docker compose version` (with a space). If it fails, update Docker; the
   older `docker-compose` command is not supported.
4. Get the new Compose file. Downloading new images does not update it. Save
   [the new file](https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml)
   as `docker-compose.new.yml` next to your current one, or run:

   ```bash
   curl -fsSL -o docker-compose.new.yml https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
   ```

5. Copy your own changes from `docker-compose.yml` into `docker-compose.new.yml`:
   folders, ports, and any
   [memory limits](../../development/profiling.md#container-memory-limits) you
   raised; set `memswap_limit` to the same value. Take everything else from the
   new file; `.env` stays as it is. Then rename the old file to
   `docker-compose.old.yml` and the new one to `docker-compose.yml`. If your NAS
   container manager keeps its own copy, paste the new file there.
6. In the folder with `docker-compose.yml`, check the file, download the new
   images, and restart the services:

   ```bash
   docker compose config --quiet
   docker compose pull
   docker compose up -d
   ```

   The first command prints nothing if the file is fine. If you use
   [offline maps](../services/maps.md), add `--profile local-maps` to the last
   two commands.

7. Check that everything is running:

   ```bash
   docker compose ps
   docker compose logs -f app brouter
   ```

8. Open **Settings → Processing** and wait until it finishes. Then click
   **Reload** when the map asks.

Keep your original GPS files in place. An update may re-read them once.

## Start with a fresh database

Some release notes recommend a fresh database. MTL Explorer then imports your
GPS and media files again.

**A fresh database deletes saved plans, manual edits, imported Health data, and
the Google Health connection. Back up first.**

1. [Back up](backup-and-restore.md) your installation.
2. In the folder with `docker-compose.yml`, stop MTL Explorer:

   ```bash
   docker compose down
   ```

3. Rename the database folder. Keep it until you are sure you don't need to go back:

   ```bash
   mv data/postgis data/postgis-old
   ```

4. Continue with [Update](#update) from step 3. MTL Explorer starts with an
   empty database.
5. If you use Google Health,
   [connect your Google account](../google-health.md#connect-the-google-account)
   again. Your settings in `.env` stay. MTL Explorer then imports your Health
   history again.
6. When **Settings → Processing** is finished, your tracks look right, and your
   Health history is back, delete `data/postgis-old`.

## Update check

The update check is optional. If the server cannot reach the internet, the app
works normally. To turn it off, add `MTL_UPDATES_ENABLED=false` to `.env`. To use
your own release feed, set `MTL_UPDATES_FEED_URL` in `.env`. Then run
`docker compose up -d`. See [release updates](../../development/releases.md)
for details.

## Pin exact versions

To run a tested combination of images, set `MTL_APP_IMAGE`,
`MTL_BROUTER_IMAGE`, `MTL_LOCATION_SEARCH_IMAGE`, and optional `MTL_MAP_IMAGE`
in `.env` to the image digests listed in the release manifest. See
[component bundles](../../development/releases.md#publishing-a-component-bundle).

To build the images yourself from the source code, see
[container build](../../development/containers.md).
