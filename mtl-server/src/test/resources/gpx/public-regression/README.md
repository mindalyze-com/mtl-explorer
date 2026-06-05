# Public GPX Regression Deck

These fixtures are public internet test files. Do not replace or extend this deck
with private user tracks.

Source: https://www.viewmygpx.com/sample-gpx-files/

License: Creative Commons Zero (CC0), public domain. The source page states that
the files include no real user data and are synthetic routes with realistic
coordinates, timestamps, elevation, and stats.

Vendored files:

| File | Purpose |
| --- | --- |
| `viewmygpx/short-hike-5km.gpx` | Small single-track hiking smoke test |
| `viewmygpx/mountain-hike.gpx` | Dense elevation and slow/variable hiking |
| `viewmygpx/road-cycling-50km.gpx` | Faster cycling cadence and longer distance |
| `viewmygpx/multi-day-hike.gpx` | Four tracks with intentional overnight gaps |
| `viewmygpx/sailing-route.gpx` | Zero-elevation track |
| `viewmygpx/extensions-test.gpx` | GPX extensions plus cycling sensor fields |

Regression intent:

- Audit source GPX structure independently from the MTL Explorer importer.
- Verify that clean realistic sources do not create impossible jumps.
- Verify that normal multi-track files do not explode into hundreds of tiny tracks.
- Verify that imported time domains stay monotonic and chart-safe.
