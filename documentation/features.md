# Features

MTL Explorer is a self-hosted GPS archive for importing, viewing, filtering,
analyzing, replaying, and planning routes from personal track data.

## Archive and import

- Watch-folder import for GPX, FIT, TCX, KML/KMZ, IGC, SBP, NMEA, GeoJSON, and GDB; non-GPX files are converted with GPSBabel.
- Garmin Connect sync/import that pulls new activity files with gcexport, skips known Garmin activity IDs, and retries selected failures through FIT export.
- Admin GPX upload, Garmin tool management, and manual GPS/media rescans from the app.
- Background indexing for GPS files and geotagged media, with progress for indexers, duplicate detection, activity classification, and exploration scoring.
- Import cleanup for bad GPS fixes, stationary drift, elevation noise, long temporal gaps, and empty or failed files.
- Raw, cleaned, and simplified track geometries for precise detail views and fast archive rendering.
- Automatic activity classification from metadata, text, and speed, with user-set activity types preserved.
- Duplicate detection that keeps the best source track and marks related imports as duplicates.

## Map

- Full archive MapLibre map with cache-first track loading, background sync, stale-data detection, and offline fallback.
- Base themes for OSM topo/light/dark and Swiss Color/Light topographic maps, backed by local PMTiles or remote raster tiles depending on deployment.
- Maps and data panel with thumbnail theme selection, layer visibility/opacity sliders, and reset defaults.
- Layer controls for basemap, tracks, photo/media, GPS points, heatmap, Waymarked Trails, and Swiss route/trail overlays.
- Waymarked worldwide hiking/cycling/MTB overlays and Swiss hiking, bike, MTB, and signposted trail overlays.
- Swiss route identification on map click when Swiss overlays are active.
- Progressive track precision, close-zoom GPS point arrows, rich point popups, and multi-track selection sheets.
- Status banners for offline cached data, stale archive refresh, and local map preparation/download progress.
- GPS locate/follow mode, heatmap, geotagged media clusters, and GeoNames-backed local location search sorted by importance or distance.

## Filters

- Filter workspace with Filter, Colors, and SQL tabs, searchable catalog groups, live preview counts, auto-apply, and SQL inspection.
- Palette previews, legend ordering, grouped-result drill-down, and map legend controls for hiding categories or gradient bands.
- Built-in filters for date/time, activity, quality, duplicate/error status, motorized/non-motorized tracks, and performance gradients.
- Time filters for year, day, quarter, day of week, and time of day.
- Gradient filters for average speed, distance, elevation gain, and energy.
- Custom SQL filters with typed parameters, including strings, date/time, circles, rectangles, and polygons.
- SQL template filters inherit grouped parameter metadata, optional flags, widgets, and track-picker sources from their included base filters.
- Map drawing tools for geo filter areas, with undo, finish, and cancel controls.

## Track details

- Overview metrics for distance, duration, moving/stopped time, stops, speed, elevation, slope, energy, power, fitness, and exploration.
- Graphs for speed, elevation, elevation gain rate, distance over time, cumulative mechanical energy, and estimated power.
- Graph controls for time/distance axes, min/max bands, point density, and chart height.
- Resizable/collapsible mini-map synchronized with graphs and events, including pinned chart points and hover cross-highlighting.
- Quality tab with load, duplicate, outlier, point-spacing, activity-source, geo-coverage, GPX metadata, and indexer details.
- Quality tab curation controls and Quality filters for excluding and refinding noisy or misclassified tracks.
- Header actions for downloading the original indexed source file or exporting the track as GPX.
- Related tab for previous/next activities, duplicates, and derived split segments.
- Events tab for detected breaks, photo stops, GPS gaps, durations, positions, and longest-break highlighting.

## Statistics and discovery

- Filter-aware Statistics workspace with Overview, Trends, and Tracks tabs.
- Server-calculated Overview dashboard with totals, activity breakdown, highlights, recent activity, active-period drill-downs, and milestones.
- Highlight drill-down actions, browser badges, and searchable curation reasons for reviewing unreliable tracks.
- Trends by total, year, quarter, month, week, or day, with optional sub-unit filtering, summary tiles, sortable tables, and aligned charts.
- Trend metrics for active days, track count, total/average duration, total/average distance, energy, estimated power, Normalized Power, intensity, training load, and exploration.
- Track browser with search, sort, pagination, shape previews, map centering, detail navigation, and energy/exploration columns.

## Energy, fitness, and exploration

- GPS-derived mechanical energy from elevation, gravity, aerodynamic drag, rolling resistance, and kinetic work.
- Track and point-level energy, estimated power, rolling 30-second power, Normalized Power, intensity, and training load.
- Activity-specific energy models for cycling, motorized road/air travel, walking, running, water sports, skiing, and default movement.
- Exploration Score for the share of a track that covers new territory compared with earlier unique tracks.
- Background recalculation when historical imports or exploration settings invalidate later scores.

## Segment analysis and replay

- Segment Analyzer with map zones, adjustable radius, live per-zone/shared candidate counts, and tracks crossing all zones.
- Results table with consolidated or per-visit rows, speed/time/distance modes, stop status, and trend charts.
- Segment comparison with speed, altitude, power, energy, slope, pacing, and time-gap charts.
- Virtual Race replay for selected segment attempts with moving markers, trails, rankings, and playback speed.
- Archive animation that replays visible tracks over time with date range and speed controls.

## Planner

- BRouter route planner with Drawing/Load tabs and trekking/hiking, road-bike, mountain-hiking, and car profiles.
- Waypoint add, drag, insert, delete, undo, redo, clear, save, and load workflows.
- Live route geometry, distance, ascent, descent, duration, leg stats, and elevation profile.
- Segment prewarm/status handling, missing-segment retries, route-leg caching, and request abort/retry support.
- Saved plans with load, delete, GPX export, waypoints, profile, legs, stats, and separation from imported activity statistics.

## Media

- Media watcher for common image and video formats with EXIF/GPS metadata.
- Full indexing for large geotagged media libraries, including hundreds of thousands of GPS-tagged photos for the map photo/media layer.
- Clustered map media layer with bounds-based loading.
- Image and video preview with metadata, previous/next navigation, prefetching, and original download.
- HEIC/HEIF conversion, resized image responses, cache headers, and byte-range video streaming.

## Admin and operations

- Admin tile workspace for drag-and-drop GPX upload, jobs, freshness tokens, Garmin sync, helper installs, server logs, build/runtime info, settings, session cleanup, and attribution.
- Data freshness domains compare server/client revision tokens and refresh stale local caches.
- Local light/dark theme, locale formatting, PWA/browser mode display, and credentials-only or full local-data logout.
- Docker/self-hosted deployment with configured GPS/media volumes, PostGIS, local map assets, and optional BRouter/map-server services.
