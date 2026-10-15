# System architecture

MTL Explorer serves a Vue PWA and Spring Boot API under `/mtl/`. PostgreSQL with PostGIS stores tracks, derived data, plans, corrections, and optional Health records. Original GPS and media files stay in mounted folders.

```text
Browser PWA → Spring Boot API → PostgreSQL and PostGIS
                   ├─ watched GPS and media folders
                   ├─ BRouter routing service
                   ├─ GeoNames location-search service
                   ├─ hosted or local PMTiles map service
                   └─ optional Garmin and Google Health connections
```

## Responsibilities

| Component | Owns |
| --- | --- |
| Server | Ingest, domain calculations, filtering, transactions, jobs, API contracts |
| Browser | Selection, display, input conversion, charts, map rendering, local preferences and caches |
| Database | Authoritative stored records, work state, spatial indexes, user changes |
| Source folders | Original files needed for downloads and normal reimport |
| Routing and search services | Routing geometry and location lookup |
| Map service | Archive delivery; server selects and proxies the upstream |

GPS ingestion runs conversion, canonical processing, indexing, and downstream jobs. Media correlation consumes supported track timing. Health synchronization maintains separate records and can publish exercise routes to normal GPS import. Saved plans stay separate from imported activity statistics.

The [database relation diagrams](database.md) show table keys and relationships.
API types come from the [exported OpenAPI schema](../../mtl-api/open-api-schema/schema.json). Browser caches must follow server revisions; neither map simplification nor chart reduction owns analytical totals.

External map, terrain, trail, account, and update services have separate availability. A browser cache and a server-local map archive do not make all calculations available offline.

See [GPS processing](gps/processing.md), [segment calculations](analysis/segments.md), [media correlation](media/correlation.md), [Health sync](health/synchronization.md), and [caching](caching-and-offline.md). Deployment services and mounts are defined by [Compose](../../docker-compose.yml).
