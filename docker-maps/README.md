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

Replacing the container image is safe when the same `/data` volume or bind mount is retained. A complete existing archive and its sentinel are reused, so an image update does not download the map again.

## Memory Settings

The supplied Compose files use a 128 MiB hard limit, disable swap by setting the swap limit to the same value, and reserve 64 MiB. The validated floor is 96 MiB; adding 20 percent gives 115.2 MiB, which is rounded up to a conventional allocation. The low-zoom overview is limited to zoom 6; higher extraction levels create much larger temporary output and need more memory.

## Access Boundary

Only PMTiles vector map files require the `mtl-version` and `mtl-server-id`
query parameters.

The requesting app sends its container release number in `mtl-version` and
image build time separately in the optional `mtl-build`, for example
`mtl-version=1.519&mtl-build=2026-10-06T10_20_30Z`. Builds without image metadata
fall back to Maven metadata; unknown build times are omitted. Access logs record
these values in `mtl_version` and `mtl_build`. The build field is not required
for authorization.

Map requests also carry the resolved client IP in `X-Forwarded-For` and
`X-Real-IP`, separately from the connecting backend's address. MTL Explorer uses
the servlet's resolved address, including its configured Spring forwarding support;
it does not copy incoming IP headers directly. The ingress proxy must remove
untrusted forwarding headers before setting its own, as described in
[Spring's forwarding guidance](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html).
Direct installations can disable incoming forwarding with
`SERVER_FORWARD_HEADERS_STRATEGY=none`.

Operational endpoints and files such as `/health`, `/status`, sentinels, and
URL marker files are outside that identifier gate. `/logs` is never served.
Location search is handled by the separate `docker-location-search` sidecar.
