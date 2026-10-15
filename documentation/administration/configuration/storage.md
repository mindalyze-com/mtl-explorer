# Data folders

MTL Explorer keeps all its data in `data/`, next to `docker-compose.yml`:

```text
./data/gpx                    your GPS files (watched for new files)
./data/media                  your photos and videos
./data/postgis                database
./data/brouter-segments       route planning data
./data/maps                   offline maps (only if enabled)
./data/location-search        place search database
./data/google-health-staging  temporary Google Health downloads
./data/logs                   application logs
```

Copy GPS files into `./data/gpx/`; MTL Explorer finds them automatically. Unzip
ZIP archives first. Put photos and videos into `./data/media/`.

Keep the whole `data/` folder when you update. Re-downloading maps and
routing data takes a long time.

## Use other folders

To keep your data or media somewhere else, edit the `app.volumes` section in
`docker-compose.yml`. Change only the left side of each line:

```yaml
volumes:
  - /your/mtl-data:/app/mtl-data
  - /your/media:/app/media:ro
```

Put your tracks in `/your/mtl-data/gpx`. Keep `gpx` and `google-health-staging`
inside the same folder; Google Health imports need them together.

## Fix permissions on Linux

If Docker created `./data/` as root and imports fail, give it back to your user:

```bash
sudo chown -R "$USER:$USER" data
```
