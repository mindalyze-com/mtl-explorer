# BRouter sidecar (route-planner feature)

This directory builds a self-contained Docker image that runs
[BRouter](https://brouter.de) — a light-weight OSM-based bicycle/hiking/car
router that fits comfortably on a Raspberry Pi or Synology NAS.

The image is **optional**. It is only required if you want the in-app
route-planner feature. All other MyTrailLog functionality works without it.

## Architecture

Two processes run inside the container under a small Python supervisor:

| Port   | Process                | Purpose                                         |
|--------|------------------------|-------------------------------------------------|
| 17777  | BRouter (Java)         | `GET /brouter?lonlats=...&profile=...&format=geojson` |
| 17778  | Admin HTTP (Python)    | `GET /status`, `GET /health`, `POST /prewarm`   |

BRouter ships with routing profiles (`trekking.brf`, `fastbike.brf`,
`hiking-mountain.brf`, `car-eco.brf`) and embedded SRTM elevation data.
It does **not** ship rd5 routing segments — those 5°×5° files are downloaded
from <https://brouter.de/brouter/segments4/> on demand and cached in the
`brouter-segments` Docker volume.

The MyTrailLog server (`PlannerAutoPrewarmService`) POSTs the bounding box of
all imported tracks to `/prewarm` on startup, so the segments covering the
user's known area are fetched in the background before the first route request.

## Run standalone (no MyTrailLog)

```bash
docker build -t mytraillog-brouter:local docker-brouter
docker run --rm -p 17777:17777 -p 17778:17778 \
  -v brouter-segments:/segments4 \
  mytraillog-brouter:local

# Once running, a minimal route request (Zürich HB → Zürich Flughafen):
curl "http://localhost:17777/brouter?lonlats=8.5402,47.3784|8.5517,47.4515&profile=trekking&alternativeidx=0&format=geojson"

# Segment status:
curl http://localhost:17778/status
```

## Run as a MyTrailLog sidecar

Defined in the root `docker-compose.yml` behind the `planner` profile so it
stays off by default.

```bash
# One-off: set MTL_PLANNER_ENABLED=true on the `app` service, then:
MTL_PLANNER_ENABLED=true docker compose --profile planner up -d
```

## File layout

```
docker-brouter/
├── Dockerfile
└── scripts/
    ├── entrypoint.sh          # calls orchestrator.py
    ├── constants.py           # all magic values (paths, ports, URLs, timeouts)
    ├── orchestrator.py        # spawns BRouter + admin HTTP + status writer
    └── segment_downloader.py  # queued rd5 downloads, bbox → tile enumeration
```

## Upgrading BRouter

Bump `BROUTER_VERSION` in the Dockerfile. Release zips live at
`https://github.com/abrensch/brouter/releases`.
