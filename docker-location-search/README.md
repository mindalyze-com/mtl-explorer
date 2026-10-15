# MTL Explorer Location Search

Lightweight GeoNames SQLite FTS5 sidecar for `/api/location-search/*`.

The image does not download GeoNames data at startup. Build
`docker-location-search/geonames-search/build/geonames-search.sqlite` first,
then build the image so the database is copied into
`/data/geonames-search.sqlite`. You can also mount a generated database at that
path.

Runtime:

- `GET /health`
- `GET /status?mtl-version=<container-version>&mtl-build=<build-time>&mtl-server-id=<server-id>`
- `GET /search?mtl-version=<container-version>&mtl-build=<build-time>&mtl-server-id=<server-id>&q=zurich&limit=20&sort=importance`

`/status` and `/search` require the same backend identity parameters as the
map sidecar, and log them for operational visibility. `mtl-build` is optional
and reports the requesting app's image build time, separately from its version.
The image writes
`MTL_IMAGE_VERSION` and `MTL_IMAGE_BUILD_TIME` into `/opt/mtl/*` at build time;
`/status` returns those values in `versionInfo`.

The runtime uses APSW `3.53.4.0`, which bundles SQLite `3.53.4`.

The image and the default Compose both use a 32 MiB SQLite mmap window. Compose
limits the container to 128 MiB, with a 64 MiB reservation and no swap. The
validated floor is 96 MiB; adding 20 percent gives 115.2 MiB, rounded up to
128 MiB. Set `MTL_LOCATION_SEARCH_MMAP_SIZE` in bytes to use a different window.
