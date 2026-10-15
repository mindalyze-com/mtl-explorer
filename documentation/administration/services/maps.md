# Map services

By default, the base map loads from an online map service through your MTL Explorer
server, so your browser doesn't contact that service for it. Other map parts,
such as fonts, symbols, 3D terrain, some map styles, and trail overlays, load
directly from their own services.

## Offline maps

To host the base map yourself, enable the `local-maps` profile:

1. Add this line to `.env`:

   ```env
   MTL_UPDATES_MAP_SERVER_MANAGEMENT=SELF_MANAGED
   ```

2. Start with the profile:

   ```bash
   docker compose --profile local-maps up -d
   ```

The first download is about 120–130 GB; keep about 200 GB free. It continues
if interrupted and is saved in `./data/maps/`. Keep this folder when you
update; a new map image reuses the downloaded maps.

Some map styles, 3D terrain, and trail overlays still load from the
internet. Offline maps on the server are not the same as using the app offline
on your phone; see [mobile and offline use](../../user/mobile-and-offline.md).

For the simpler online setup, leave the `local-maps` profile off.
