# Back up and restore

Your GPS files alone are not enough to recover everything. The database also
holds your saved plans, corrections, review decisions, and health records.

Back up:

- the database and its user accounts (commands below)
- your original GPS files, photos, and videos
- `docker-compose.yml`, `.env`, and any override files
- other folders you mounted yourself

Store the backup privately on another disk or computer; it contains passwords
and personal data. Note which image versions you were running.

## Create a consistent backup

Run these commands in the folder with `docker-compose.yml`. Replace the example
path with your backup location. MTL Explorer is stopped while the backup runs.

```sh
umask 077
mkdir -p /path/to/private-backup/mtl-explorer
docker compose stop app
docker compose exec -T db pg_dump -U mtluser -d mtl -Fc > /path/to/private-backup/mtl-explorer/database.dump
docker compose exec -T db pg_dumpall -U mtluser --roles-only > /path/to/private-backup/mtl-explorer/roles.sql
```

Check that both commands succeeded and both files are not empty. `roles.sql`
contains password hashes; keep it private. Without it, filters can stop
working after a restore.

Now copy `docker-compose.yml`, `.env`, override files, and your GPS, photo, and
video folders. Copy the map and routing data too if you don't want to download
it again. Don't add or change files while copying.

Don't copy the `data/postgis/` folder instead of making a dump; a copy of a
running database may not restore. To check the dump, list its contents:

```sh
docker compose exec -T db pg_restore --list < /path/to/private-backup/mtl-explorer/database.dump
```

Start MTL Explorer again when the backup is complete:

```sh
docker compose start app
```

## Restore into a separate installation

1. Create a new installation directory. Restore configuration and source folders.
   Keep the same paths inside containers and use a fresh database directory.
2. Pin compatible images from the backup. Use separate host ports if the original
   instance is still running.
3. Bootstrap the new database with an administrator name absent from the role
   backup. For example, add this to `compose.override.yml`, merging any existing
   overrides:

```yaml
services:
  db:
    environment:
      POSTGRES_USER: mtl_restore_admin
      POSTGRES_DB: mtl_restore_bootstrap
    healthcheck:
      test: ["CMD", "pg_isready", "-h", "127.0.0.1", "-U", "mtl_restore_admin", "-d", "mtl_restore_bootstrap"]
```

Keep the restored database username and password unchanged. The temporary
administrator avoids conflicts when the original accounts are restored.
Start only the database, leaving the app stopped:

```sh
docker compose up -d db
docker compose exec -T db pg_isready -h 127.0.0.1 -U mtl_restore_admin -d mtl_restore_bootstrap
```

When the database is ready, restore the accounts first, then the data:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U mtl_restore_admin -d mtl_restore_bootstrap < /path/to/private-backup/mtl-explorer/roles.sql
docker compose exec -T db createdb -U mtl_restore_admin -O mtluser -T template0 mtl
docker compose exec -T db pg_restore --exit-on-error --no-owner -U mtluser -d mtl < /path/to/private-backup/mtl-explorer/database.dump
```

Before starting the app, give the restored installation a new identity. This
makes browsers discard cached tracks from before the restore, including when
you restore to the same address:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U mtluser -d mtl -c "DELETE FROM config WHERE domain1 = 'SECURITY' AND domain2 = 'SERVER_ID';"
docker compose up -d
```

If you use Google Health, keep the same client secret in `.env`. With a new
secret you must reconnect Google Health; your stored health data stays.

Check the restored installation: login, number of tracks, a few tracks, saved
plans, photo corrections, and review decisions. Download one original file.
Wait for **Settings → Processing** to finish before you reload. Keep the old
installation until you are sure the new one works.

Going back to an older image does not undo data changes. To return to an older
version, restore a backup made with that version.
