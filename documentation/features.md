# Features

MTL Explorer is a self-hosted GPS archive for importing, viewing, filtering,
analyzing, replaying, and planning routes from personal track data.

## Archive and import

- Watch-folder import for GPX, FIT, TCX, KML/KMZ, IGC, NMEA, GeoJSON, and GDB; non-GPX files are converted with GPSBabel.
- Garmin Connect sync/import that pulls new activity files with gcexport, skips known Garmin activity IDs, and retries selected failures through FIT export.
- Admin track-file upload for supported GPS formats, Garmin tool management, and manual GPS/media rescans from the app.
- Background indexing for GPS files and geotagged media, with progress for indexers, duplicate detection, activity classification, and exploration scoring.
- Import cleanup for bad GPS fixes, stationary drift, elevation noise, long temporal gaps, and empty or failed files.
- Raw, cleaned, and simplified track geometries for precise detail views and fast archive rendering.
- Automatic activity classification from metadata, text, and speed, with user-set activity types preserved.
- Duplicate detection that keeps the best source track and marks related imports as duplicates.

## Map

- Full archive MapLibre map with cache-first track loading, background sync, stale-data detection, and offline fallback.
- Base themes for OSM Topo Contrast, OSM Topo Light, OSM Light/Gray/Dark, and Swiss Topo Color/Light maps, backed by local PMTiles or remote raster tiles depending on deployment.
- Responsive Map settings workspace with a current-map overview and focused screens for themes, terrain, personal data, and route overlays.
- Opt-in 3D terrain view using Mapterhorn elevation tiles, with synchronized base/overlay maps and a compact map control.
- Layer controls for basemap, tracks, photo/media, GPS points, heatmap, Waymarked Trails, and Swiss route/trail overlays.
- Waymarked worldwide hiking/cycling/MTB overlays and Swiss hiking, bike, MTB, and signposted trail overlays.
- Swiss route identification on map click when Swiss overlays are active.
- Progressive track precision, close-zoom GPS point arrows, rich point popups, and multi-track selection sheets.
- Status banners for offline cached data, stale archive refresh, and local map preparation/download progress.
- GPS locate/follow mode, heatmap, geotagged media clusters, and GeoNames-backed local location search sorted by importance or distance.

## Filters

- Filter workspace with the current view, a searchable view picker, live result status, pause/resume, reset with undo, and secondary SQL inspection.
- Quick category controls and a staged category manager can narrow grouped filters, including activities, years, weekdays, custom groups, and numeric buckets.
- Selected categories apply to the map, browser, heatmap, statistics, related tracks, and segment analysis.
- Collapsible map-color controls, stable category colors, track review, and map-only visibility controls with Show all.
- Built-in filters for date/time, activity, quality, duplicate/error status, motorized/non-motorized tracks, and performance gradients.
- Activity filters expose both broad main groups and exact types such as Walking, Hiking, and Bicycle.
- Time filters for year, day, quarter, day of week, and time of day.
- Gradient filters for average speed, distance, elevation gain, and energy.
- Custom SQL filters with typed parameters, including strings, date/time, circles, rectangles, and polygons.
- SQL template filters inherit grouped parameter metadata, optional flags, widgets, and track-picker sources from their included base filters.
- Map drawing tools for geo filter areas, with undo, finish, and cancel controls.
- Phone and desktop layouts use the same filter flow, with full-width inner screens on phones.

## Track details

- Overview metrics for distance, duration, moving/stopped time, stops, speed, elevation, slope, energy, power, fitness, and exploration.
- Graphs for speed, elevation, elevation gain rate, distance over time, cumulative mechanical energy, and estimated power.
- Graph controls for time/distance axes, min/max bands, point density, and chart height.
- Resizable/collapsible mini-map synchronized with graphs and events, including pinned chart points and hover cross-highlighting.
- 3D track replay from Track Details with duration presets, cinematic camera modes, smoothed camera rails, terrain-aware route progress, and 2D fallback.
- Quality tab with load, duplicate, outlier, point-spacing, activity-source, geo-coverage, GPX metadata, and indexer details.
- Overview/Quality curation controls and Quality filters for correcting activity type, excluding unreliable tracks, and refinding noisy or misclassified tracks.
- Header actions for downloading the original indexed source file or exporting the track as GPX.
- Related tab for previous/next activities, duplicates, and derived split segments.
- Photos tab with a server-paged chronological timeline, explicit Photo GPS / Estimated / Set by you origins, and color-coded circular mini-map markers. Rare camera-clock and manual-location corrections stay in a collapsed Photo tools panel.
- Responsive photo viewer defaulting to dark, with a viewer-only persisted light/dark toggle, desktop photo/details layout, phone-first media and native-scrolling details, progressive file and capture metadata, bounded Nearby thumbnails, track-aware location map, keyboard/swipe navigation, and zoom/pan.
- Separate panel-maximize and browser-fullscreen window modes; selecting one replaces the other, while Details and Nearby remain independently controllable.
- Events tab for detected breaks, photo stops, GPS gaps, durations, positions, and longest-break highlighting.

## Statistics and discovery

- Filter-aware Statistics workspace with Overview, Trends, and Tracks tabs.
- Server-calculated Overview dashboard with totals, activity breakdown, highlights, recent activity, active-period drill-downs, and metric or US customary milestones.
- Highlight drill-down actions, browser badges, and searchable curation reasons for reviewing unreliable tracks.
- Trends by total, year, quarter, month, week, or day, with optional sub-unit filtering, summary tiles, sortable tables, and aligned charts.
- Final Media trend chart defaulting to all indexed media, with an optional explained Track related scope, stacked photo/video counts, zero-filled periods aligned with the other charts, separate undated media, and a paged/filterable period mosaic.
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
- Responsive archive animation workspace with date-range preview, human-readable speed controls, and a compact map-first playback bar.
- Single-track 3D replay for inspecting one activity as a cinematic fly-through.

## Planner

- BRouter route planner with Drawing/Load tabs and trekking/hiking, road-bike, mountain-hiking, and car profiles.
- Waypoint add, drag, insert, delete, undo, redo, clear, save, and load workflows.
- Live route geometry, distance, ascent, descent, duration, leg stats, and elevation profile.
- Segment prewarm/status handling, missing-segment retries, route-leg caching, and request abort/retry support.
- Saved plans with load, delete, GPX export, waypoints, profile, legs, stats, and separation from imported activity statistics.

## Media

- Media watcher for common image and video formats with embedded GPS and capture metadata.
- Full indexing for large geotagged media libraries, including hundreds of thousands of GPS-tagged photos for the map photo/media layer.
- Clustered map media layer with bounds-based loading, enabled by default with a persisted visibility preference. Every marker opens a chooser for the clicked photo/location or cluster, the current map view, and GPS activities at the clicked location when available. The viewer keeps the chosen scope visible and loads further bounded cluster pages as needed without changing map zoom.
- Activity media timelines and mini-maps use bounded server pages, keeping list, marker, and viewer work stable for activities with 100,000 photos.
- Image and video viewer with capture date and source, file type and size, photo dimensions and exposure, video duration and stream details, coordinates and embedded altitude, modified time and folder. Technical fields use a mobile-friendly disclosure; the viewer also includes a collapsible 200-thumbnail sliding filmstrip with bidirectional page loading, keyboard/swipe navigation, prefetching, exclusive maximize/fullscreen window modes, independent panel controls, zoom, bounded pan, a track-aware location map, main-map return, and original download.
- Server-side activity matching uses authoritative GPS time or offset-adjusted camera time, keeps EXIF and track-interpolated position provenance separate, and does not rewrite source metadata.
- Demo mode generates deterministic 1920×1440 JPEG photos with complete GPS EXIF; roughly half match a nearby activity and half remain standalone.
- HEIC/HEIF conversion, resized image responses, generated JPEG video posters, cache headers, and byte-range video streaming. If native video decoding fails, the viewer can request a temporary H.264/AAC HLS stream, select a bounded quality profile, show conversion progress, reconnect to prepared work, cancel it, and preserve the original download.
- Media reindex and removal cleanup with retained source-file status and database audit snapshots for derived media rows.

## Admin and operations

- Responsive Admin center with route-backed sections for imports and Garmin sync, processing, data status, maintenance, server logs, system information, preferences, and session cleanup.
- Desktop Admin navigation stays beside the selected section; mobile uses an overview and drill-in flow. Libraries and data-source credits are listed on the public About page.
- Data freshness domains compare server/client revision tokens and refresh stale local caches.
- Local light/dark theme, locale formatting, metric or US customary measurements, PWA/browser mode display, and credentials-only or full local-data logout.
- Docker/self-hosted deployment with configured GPS/media volumes, PostGIS, local map assets, and optional BRouter/map-server services.
