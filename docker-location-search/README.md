# MTL Explorer Location Search

Lightweight GeoNames SQLite FTS5 sidecar for `/api/location-search/*`.

The image does not download GeoNames data at startup. Build
`docker-location-search/geonames-search/build/geonames-search.sqlite` first,
then build the image so the database is copied into
`/data/geonames-search.sqlite`. You can also mount a generated database at that
path.

Runtime:

- `GET /health`
- `GET /status?mtl-version=<backend-version>&mtl-server-id=<server-id>`
- `GET /search?mtl-version=<backend-version>&mtl-server-id=<server-id>&q=zurich&limit=20&sort=importance`

`/status` and `/search` require the same backend identity parameters as the
map sidecar, and log them for operational visibility. The image writes
`MTL_IMAGE_VERSION` and `MTL_IMAGE_BUILD_TIME` into `/opt/mtl/*` at build time;
`/status` returns those values in `versionInfo`.

The runtime uses APSW `3.53.1.0`, which bundles SQLite `3.53.1`.

The default Compose uses a 32 MiB SQLite mmap window, a 128 MiB container limit,
a 64 MiB reservation, and no swap. The validated floor is 96 MiB; adding 20
percent gives 115.2 MiB, which is rounded up to a conventional allocation. Set
`MTL_LOCATION_SEARCH_MMAP_SIZE` in bytes when a different window is needed.
