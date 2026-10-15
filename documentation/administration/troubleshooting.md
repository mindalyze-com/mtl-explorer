# Diagnose an installation

First check that all services are running, then read the latest log lines:

```sh
docker compose ps
docker compose logs --tail=100 app db brouter location-search
```

| Problem | What to check |
| --- | --- |
| App does not start | Is the database ready? Do the image and Compose file belong to the same version? Can the app read its folders? |
| Files are not imported | Is the GPS folder mounted where you expect? Can the app read it? Is the format supported? Open **Review** for errors. |
| Offline maps missing | Is the `local-maps` profile on? Has the download finished? Is there enough disk space? |
| Route planning fails | Is routing data downloaded? Check the BRouter log. |
| Place search finds nothing | Is the `location-search` service running? |
| Google Health cannot connect | See [Google Health setup](google-health.md#diagnose-google-health). |
| Browser shows old data | Open **Settings → Data status**, or reload when the app asks. |

Fix the cause before restarting again and again. Keep your original files and
backups. To recover data, see [backup and restore](maintenance/backup-and-restore.md).
