# Garmin setup

Garmin sync is optional and off by default. It downloads your Garmin activities
into the GPS folder, where MTL Explorer imports them.

1. Create `application.yml` next to `docker-compose.yml` with your Garmin login:

   ```yaml
   mtl:
     garmin-sync:
       enabled: true
       user-name: your-garmin-login
       user-password: your-private-password
   ```

   This file contains your password. Allow only your user to read it.

2. Create or extend `compose.override.yml` next to it. If it already has an
   `app` section, add these lines to it:

   ```yaml
   services:
     app:
       volumes:
         - ./application.yml:/app/config/application.yml:ro
         - ./data/gpx:/app/gpx
   ```

   Keep the existing `./data:/app/mtl-data` line in `docker-compose.yml`.

3. Apply the changes:

   ```bash
   docker compose up -d
   ```

4. Open **Settings → Import & sync → Garmin → Sync now**. Check
   **Garmin command output**, then wait until **Settings → Processing** is done.

Some downloads can fail. MTL Explorer retries failed downloads in a different
format, but not every error can be fixed this way. See
[Garmin export](../architecture/garmin-export.md) for how it works.
