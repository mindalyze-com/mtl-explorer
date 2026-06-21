# MTL Explorer Release v.0.6.0

## Features

- Added cinematic 3D track replay from Track Details, with playback controls, duration presets, camera styles, telemetry, and a 2D fallback when 3D is not available.
- Added a richer, more colorful map style that makes roads, terrain, and route context easier to read at a glance.
- Added an optional 3D terrain map view so routes can be inspected with elevation context directly on the main map.
- Expanded track import beyond GPX/FIT with support for more GPS file formats, including TCX, KML/KMZ, IGC, NMEA, GeoJSON, and GDB.
- Added original-file download and GPX export actions from Track Details, making it easier to recover or share imported activities.
- Improved Track Details with stronger overview metrics, energy and power views, synced graphs, mini-map interaction, events, quality information, and related activities.
- Expanded Statistics with a richer overview, activity breakdowns, highlights, milestones, recent activity, and better review tools for tracks excluded from statistics or highlights.
- Improved route and segment analysis with better comparison views, virtual race playback, archive animation, and planner workflows.
- Added a broader Admin workspace for uploads, indexing status, data freshness, Garmin sync, helper tools, logs, runtime information, settings, session cleanup, and attribution.

## Fixes And Changes

- Reworked map settings so map style, visible layers, opacity, terrain settings, and overlay choices persist more reliably.
- Improved local and remote map behavior, including raster fallback, attribution display, cache handling, and several additional map styles.
- Improved GPS file conversion reliability, especially for KMZ and GeoJSON, and removed the unsupported SBP format from the documented supported list.
- Improved activity-type correction so user changes are saved consistently and related energy values are recalculated.
- Added rider-weight adjustment for energy estimates, including preview and save behavior in Track Details.
- Improved filter behavior for date, text, track-id, SQL-template, keyword, year, and geo-shape parameters.
- Improved data freshness handling so imports, deletes, rescans, and server-side changes can refresh the client without repeated reload loops.
- Improved mobile sheets, map controls, planner interaction, and several responsive layouts.
- Improved session and error recovery behavior, including expired-login handling and recovery from failed map, media, planner, and track-detail requests.
- Improved performance and maintainability across the map, track cache, statistics, mini-map synchronization, and replay flows.
- Added a much larger regression and unit-test suite covering frontend flows, backend GPS processing, conversion, energy, security, map status, statistics, and API contracts.
- Updated public-facing documentation and release evidence, including full-regression and container-build reports.
