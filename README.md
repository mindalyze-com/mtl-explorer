# MTL Explorer

> Self-hosted GPS track & trail log — built to run on your own PC, in your
> home lab, or on your own server, in a few Docker containers.

# Start here

| 🏠 Home page | 🧭 Live demo | ▶️ Intro video |
| --- | --- | --- |
| **[Visit the home page](https://mindalyze-com.github.io/mtl-explorer/)** | **[Try the live demo](https://mtl-demo.mindalyze.com/mtl/)** | **[Watch on YouTube](https://youtu.be/OesCpZ0JzLc)** |
| Overview, screenshots, and feature tour. | No account needed; loaded with demo tracks. | Three-minute walkthrough of the core experience. |

---

[![License: AGPL v3+](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue.svg)](LICENSE)
[![Commercial License](https://img.shields.io/badge/commercial-available-green.svg)](COMMERCIAL-LICENSE.md)

## What it is

MyTrailLog is an open source GPS tracker / trail log web app. You import
tracks from Garmin / GPX / FIT, explore them on an offline-capable map,
analyze stops, energy, statistics, plan new routes with BRouter, and keep
everything on hardware you control.

It is split into:

- **`mtl-server/`** — Spring Boot backend (REST API, GPS pipeline, indexer,
  planner, async jobs, PostgreSQL).
- **`mtl-client/`** — Vue 3 + TypeScript PWA.
- **`mtl-api/`** — OpenAPI schema + generated TypeScript clients.
- **`docker-maps/`** — self-hosted OSM map tiles.
- **`docker-brouter/`** — self-hosted BRouter routing engine.
- **`docker/garmin_*`, `docker/gpx_porto_taxi_dataset/`** — import & demo
  pipelines.

## Quick start (self-hosted, home use)

```bash
git clone https://github.com/mindalyze-com/mtl-explorer.git
cd mtl-explorer
docker compose up -d
# open http://localhost:8080
```

The demo compose file (`docker-compose-demo.yml`) ships a fully populated
demo dataset (Porto taxi tracks) so you can explore the UI without
importing anything first.

## License

MyTrailLog is **dual-licensed** — free for personal/home use (AGPL), commercial license available for SaaS/proprietary use.

<details>
<summary>License details</summary>

| Use case | License |
| --- | --- |
| Personal / home / self-hosted / private use | [AGPL-3.0-or-later](LICENSE) (free) |
| Modifying & redistributing (fork) | [AGPL-3.0-or-later](LICENSE) — must publish complete corresponding source |
| Offering it as a hosted service (SaaS) to third parties | **Either** comply with AGPL (publish your changes) **or** buy a [commercial license](COMMERCIAL-LICENSE.md) |
| Embedding in a proprietary / closed-source product | [Commercial license](COMMERCIAL-LICENSE.md) required |

This means:

- **You can use MyTrailLog at home, for free, forever.**
- **You cannot take this code, close it off, and resell it without giving
  back.** Any modifications must either be published under AGPL *or* you
  must obtain a commercial license.
- The AGPL explicitly covers **SaaS** — simply exposing a modified version
  over the network counts as distribution.

See also:

- [`LICENSE`](LICENSE) — full AGPL-3.0 text.
- [`NOTICE`](NOTICE) — copyright notices.
- [`COMMERCIAL-LICENSE.md`](COMMERCIAL-LICENSE.md) — when and how to obtain a
  commercial license.
- [`TRADEMARK.md`](TRADEMARK.md) — "MyTrailLog" is a trademark; forks must
  rename.
- [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) — dependencies &
  bundled assets.

Commercial inquiries: **hey.lueg@gmail.com**.

</details>

## Contributing

Contributions are welcome! Please read
[`CONTRIBUTING.md`](CONTRIBUTING.md) — every contributor must sign the
[CLA](CLA.md) before their PR can be merged, so the project can keep
offering the commercial-license option.

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)

## Disclaimer

<details>
<summary>Disclaimer & limitation of liability</summary>

MyTrailLog is provided **"AS IS" without any warranty**, express or implied.
**It is not a safety-critical navigation system.** Do not rely on it as the
sole means of navigation in the backcountry, at sea, in the air, or in any
situation where inaccuracy could cause injury or loss of life. Always carry
a map, compass, and working brain.

See [`DISCLAIMER.md`](DISCLAIMER.md) for the full disclaimer and limitation
of liability.

</details>

---

Copyright © 2020-2026 Patrick Heusser and MyTrailLog contributors.
