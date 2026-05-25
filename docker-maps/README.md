# MTL Explorer Maps Container

This image serves the local PMTiles vector map archive.

> [!TIP]
> This container is optional. By default, MTL Explorer uses hosted maps. If you want the simple, zero-download setup, follow the **[README Quick Start](../README.md#quick-start)** without enabling the local maps profile.

The image does not contain map data. Runtime data is mounted at `/data`, so container updates can reuse the existing large map archive.

## Runtime Layout

- `/data/prod`: versioned PMTiles files, sentinels, and symlinks such as `planet.pmtiles`.
- `/data/logs`: nginx access/error logs and logrotate state.
- `/app`: container scripts and this README.

## Startup Flow

The container starts nginx immediately and then waits for `PUT /kickoff/prod`.

On kickoff, the orchestrator reuses an existing complete `planet.pmtiles` when present, otherwise it downloads or extracts according to the configured map settings.

The normal map server never needs to delete `/data` during an update. Do not use `docker compose down -v` or delete the map volume unless the map archive should be intentionally removed.

## Access Boundary

Only PMTiles vector map files require the `mtl-version` and `mtl-server-id` query parameters.

Operational endpoints and files such as `/health`, `/status`, sentinels, URL marker files, and logs are outside that identifier gate. Location search is handled by the separate `docker-location-search` sidecar.
