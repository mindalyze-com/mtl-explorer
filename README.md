# MTL Explorer

> Self-hosted GPS track and trail log for your own PC, home lab, or server.

## Start here

| 🏠 Home page | 🧭 Live demo | ▶️ Intro video |
| --- | --- | --- |
| **[Visit the home page](https://mindalyze-com.github.io/mtl-explorer/)** | **[Try the live demo](https://mtl-demo.mindalyze.com/mtl/)** | **[Watch on YouTube](https://youtu.be/OesCpZ0JzLc)** |
| Overview, screenshots, and feature tour. | No account needed; loaded with demo tracks. | Three-minute walkthrough. |

---

[![License: AGPL v3+](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue.svg)](LICENSE)
[![Commercial License](https://img.shields.io/badge/commercial-available-green.svg)](documentation/legal/COMMERCIAL-LICENSE.md)

## What it is

MTL Explorer imports Garmin, GPX, and FIT tracks, shows them on a map, analyzes
track statistics, and can plan routes with BRouter. It is designed for
self-hosting with Docker Compose.
Location search runs in a separate GeoNames SQLite sidecar.

See the [feature overview](documentation/features.md) for the current capability list.

[Why I built MTL Explorer](why-i-built-mtl-explorer.md): the project started
from the need to see thousands of Garmin tracks on one map, then grew into a
personal exploration, planning, and statistics tool.

Repository layout:

- `mtl-server/` - Spring Boot backend.
- `mtl-client/` - Vue 3 and TypeScript PWA.
- `mtl-api/` - OpenAPI schema and generated TypeScript clients.
- `docker-brouter/` - BRouter sidecar.
- `docker-maps/` - optional local vector map sidecar.
- `docker-location-search/` - GeoNames SQLite location-search sidecar.
- `docker/` - import and demo-data helpers.

## Quick start

Prerequisite: Docker with Docker Compose.

1. Start the stack:

   * **macOS, Linux, WSL, or Git Bash**:
     ```bash
     mkdir mtl-explorer && cd mtl-explorer
     curl -fsSL -o docker-compose.yml https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
     docker compose up -d
     ```
   * **Windows PowerShell**:
     ```powershell
     mkdir mtl-explorer; cd mtl-explorer
     Invoke-WebRequest -Uri "https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml" -OutFile "docker-compose.yml"
     docker compose up -d
     ```

   *(Note: The server takes about 10-15 seconds to boot up and initialize the database on first run.)*

2. Open **`http://localhost:18080/mtl/`** and log in:
   * **User**: `mtl`
   * **Password**: `change-me`

To import your tracks, copy GPX/FIT files into the `./data/gpx/` folder.

Change the default login before exposing MTL Explorer outside your home
network. For custom logins, directories, offline maps, and reverse proxy setup,
see the [home install guide](documentation/home-install.md).

## Documentation

- [Home install](documentation/home-install.md) - data folders, maps, updates,
  and logs.
- [Container build](documentation/container-build.md) - build local Docker
  images from a source checkout.
- [Legal documents](documentation/legal/README.md)
- [Contributing](.github/CONTRIBUTING.md)
- [Security policy](.github/SECURITY.md)

## License

<details>
<summary>License summary</summary>

MTL Explorer is dual-licensed: AGPL for free personal/home use, with a
commercial license available for proprietary or hosted use.

- [AGPL license](LICENSE)
- [Commercial license](documentation/legal/COMMERCIAL-LICENSE.md)
- [Trademark](documentation/legal/TRADEMARK.md)
- [Third-party licenses](documentation/legal/THIRD_PARTY_LICENSES.md)

</details>

## Disclaimer

<details>
<summary>Disclaimer summary</summary>

MTL Explorer is not a safety-critical navigation system. See the
[full disclaimer](documentation/legal/DISCLAIMER.md).

</details>

---

Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
