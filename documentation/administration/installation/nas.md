# Install on a NAS

1. Check the NAS against the [host requirements](requirements.md): processor
   type, memory, free ports, and disk space.
2. Create a folder for MTL Explorer on a NAS share that is kept permanently,
   not a temporary or cache folder. Keep `docker-compose.yml`, `.env`, and
   `data/` together in that folder.
3. Install with [Docker Compose](quick-start.md), using the NAS container
   manager or a terminal.
4. Open `http://<nas-address>:18080/mtl/` from a browser on the same network.
5. [Change the login](../configuration/authentication.md). Set up
   [HTTPS](../configuration/reverse-proxy.md) if you want to reach it from outside.
6. Test one import, one planned route, and a
   [backup and restore](../maintenance/backup-and-restore.md).

Make sure MTL Explorer can read your GPS folder and write to `data/`. If imports
fail with "permission denied", check the folder permissions in your NAS settings.

## Updating

Some NAS container managers keep their own copy of the Compose file. When you
[update](../maintenance/updates.md), replace that copy with the new Compose file
too; new images alone are not enough. Then recreate the containers in the
container manager; a plain restart does not apply changed settings.
