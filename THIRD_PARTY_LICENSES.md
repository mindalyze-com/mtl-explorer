# Third-Party Licenses

MyTrailLog depends on and bundles open source components from many excellent
projects. This file is a human-readable index; the **authoritative** license
texts for each dependency are shipped with that dependency (in its JAR /
npm package / Python wheel / Docker image).

## How to regenerate full license reports

### Backend (Java / Maven)

```bash
cd mtl-server
./mvnw org.codehaus.mojo:license-maven-plugin:aggregate-add-third-party \
       -Dlicense.outputDirectory=target/generated-sources/license
```

The generated file `target/generated-sources/license/THIRD-PARTY.txt`
lists every runtime dependency and its declared license.

### Frontend (Node / npm)

```bash
cd mtl-client
npx license-checker --production --summary
npx license-checker --production --json > doc/third-party-licenses.json
```

### Docker images

Each `docker-*/Dockerfile` pins the upstream base images and tools. See the
respective `README.md` for upstream licenses (OSM, BRouter, gcexport, etc.).

## Key components & their licenses (non-exhaustive, for orientation)

| Component | Role | License |
| --- | --- | --- |
| Spring Boot, Spring Framework | Backend framework | Apache-2.0 |
| Hibernate ORM | Persistence | LGPL-2.1 / Apache-2.0 |
| PostgreSQL JDBC driver | DB driver | BSD-2-Clause |
| Liquibase | DB migrations | Apache-2.0 |
| Vue.js, Vue Router, Pinia | Frontend framework | MIT |
| PrimeVue | UI components | MIT |
| Leaflet / MapLibre GL | Maps | BSD-2-Clause / BSD-3-Clause |
| Vite, Vitest | Build / test | MIT |
| TypeScript | Compiler | Apache-2.0 |
| BRouter | Routing engine (Docker) | MIT (see docker-brouter/) |
| OpenStreetMap data / tiles | Map data (Docker) | ODbL — see docker-maps/ |
| Porto Taxi Trajectory dataset | Demo data | see docker/gpx_porto_taxi_dataset/DATASOURCE.md |
| Garmin Connect Export (gcexport) | Demo importer | see docker/garmin_export/ |

> **Note:** This table is illustrative. The authoritative list is whatever
> the regenerated reports above produce for the current commit.

## AGPL compatibility

All direct runtime dependencies of `mtl-server` and `mtl-client` are under
licenses compatible with AGPL-3.0-or-later (Apache-2.0, MIT, BSD, LGPL,
MPL-2.0). Before adding a new dependency, check compatibility — see
[CONTRIBUTING.md §4](CONTRIBUTING.md).

## Data & assets

- **Map tiles & routing graphs** are generated from OpenStreetMap data,
  © OpenStreetMap contributors, available under the
  [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).
  Any tile server you run from the `docker-maps/` image must display the
  required OSM attribution.
- **Demo GPS tracks** in `docker/gpx_porto_taxi_dataset/` are derived from a
  public research dataset; see that folder's `DATASOURCE.md` for exact terms.
- **Icons and images** under `mtl-client/public/` and
  `mtl-product-page/` are © Patrick Heusser unless otherwise noted, and
  licensed under AGPL-3.0-or-later together with the source code — **except**
  for the "MyTrailLog" name and logo, which are trademarks (see
  [TRADEMARK.md](TRADEMARK.md)).
