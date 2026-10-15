# Host requirements

MTL Explorer runs several services together: the app, a database, and route
planning. It needs more memory than a small web app.

- Docker with Compose. On Windows and macOS, install Docker Desktop.
- A 64-bit Intel/AMD (`linux/amd64`) or ARM (`linux/arm64`) host.
- At least 4 GB of memory; 6–8 GB is more comfortable.
- Free ports `18080` (app) and `18083` (location search). Port `18081` is only
  needed for optional offline maps.
- A stable internet connection for the online maps. Offline maps for the whole
  world need about 200 GB of free disk space.

By default the services use about 2.5 GB of memory together. If you have many
photos and videos or very long tracks, give the app more memory. See
[memory tuning](../../development/profiling.md#container-memory-limits).

Use `docker compose` (with a space). The older `docker-compose` command is not
supported.
