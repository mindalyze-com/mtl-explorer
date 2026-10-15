# Install with Docker Compose

You need Docker with Compose. Check the [host requirements](requirements.md) first.

1. Create a folder for MTL Explorer and start it.

   On macOS, Linux, WSL, or Git Bash:

   ```sh
   mkdir mtl-explorer
   cd mtl-explorer
   curl -fsSL -o docker-compose.yml https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
   docker compose up -d
   ```

   On Windows PowerShell:

   ```powershell
   mkdir mtl-explorer
   cd mtl-explorer
   Invoke-WebRequest -Uri "https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml" -OutFile "docker-compose.yml"
   docker compose up -d
   ```

   The first start downloads the images and sets up the database. This can
   take several minutes.

2. Open `http://localhost:18080/mtl/` and sign in with user `mtl` and password
   `change-me`.

3. **Before other people or the internet can reach it,
   [change the password](../configuration/authentication.md).**

4. Upload one [supported GPS file](../../user/importing-tracks/formats.md) and
   check that it appears on the map. [Get started](../../user/getting-started.md)
   walks you through it.

Everything MTL Explorer stores goes into the `data/` folder next to
`docker-compose.yml`. Keep this folder: it holds your database, maps, and
routing data.

## If it does not start

Check that all services are running and read their latest log lines:

```sh
docker compose ps
docker compose logs --tail=100 app db brouter location-search
```

See [troubleshooting](../troubleshooting.md) for common problems.
