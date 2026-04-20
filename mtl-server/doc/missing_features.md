# MyTrailLog — Feature Gap Analysis

*Generated 30 March 2026 — based on comprehensive scan of mtl-client, mtl-server, and API schema*

## What's Already There (strong foundation)

Map with 6 themes + Swiss overlays, track display at 3 precisions, elevation/speed/power charts with cross-chart sync, photo clustering, dark mode, PWA offline, filters, animation/playback, measurement tool, virtual race (early), statistics by time period, related tracks, duplicate detection, Garmin import, energy calculation, track quality view.

---

## Missing / Improvement Opportunities

### TIER 1 — High Impact (core features users expect)

| # | Feature | What's Missing | Impact |
|---|---------|---------------|--------|
| **1** | **Live GPS recording** | `GpsLocate` component only watches position — no track recording, pause/resume, or saving a new track from the phone. Strava/Garmin's core feature. | ⭐⭐⭐⭐⭐ |
| **2** | **Route planning / navigation** | No way to plan a route before going out — draw on map, get turn-by-turn cues, export GPX. Komoot's killer feature. | ⭐⭐⭐⭐⭐ |
| **3** | **Personal records / segment leaderboards** | No automatic detection of PRs (fastest 5k, biggest climb, longest ride). No segment-based comparison. Strava's most engaging feature. | ⭐⭐⭐⭐⭐ |
| **4** | **Activity type-aware UI** | 13+ activity types exist in the backend, but the client doesn't adapt icons, metrics, or charts per type (e.g., show cadence for running, show laps for swimming). | ⭐⭐⭐⭐ |
| **5** | **Heart rate / cadence / temperature charts** | Charts cover elevation, speed, distance, acceleration, energy, power — but no HR, cadence, or temperature graphs even though Garmin FIT files contain this data. | ⭐⭐⭐⭐ |
| **6** | **Training load / fitness trend** | No weekly training summary, fitness/fatigue model (CTL/ATL/TSB), or training load tracking. Key for serious athletes. | ⭐⭐⭐⭐ |
| **7** | **Search & sort tracks** | Track browser has search with highlighting, but no sort by distance/duration/elevation/date, no advanced search (by area, by route similarity). | ⭐⭐⭐⭐ |

### TIER 2 — Medium-High Impact (differentiation & delight)

| # | Feature | What's Missing | Impact |
|---|---------|---------------|--------|
| **8** | **Heatmap layer** | Overlay all tracks as a heatmap to see where you ride/hike most. Very popular in Strava. All track GeoJSON is already available — just need a heatmap render mode. | ⭐⭐⭐⭐ |
| **9** | **Yearly summary / wrapped** | Strava Wrapped/Year in Sport — total stats, highlights, maps of all activities, personal records for the year. Stats by time period exist, but no dedicated yearly showcase. | ⭐⭐⭐⭐ |
| **10** | **Track editing** | No way to crop, split, merge, or trim tracks. No manual GPS point correction. Useful for cleaning up bad recordings. | ⭐⭐⭐⭐ |
| **11** | **Export functionality** | No GPX/KML/TCX export from the client. Users can import but not get data back out. | ⭐⭐⭐⭐ |
| **12** | **Elevation profile on route hover** | When hovering over a track on the map, show a mini elevation profile tooltip. Data exists; it's a UX enhancement. | ⭐⭐⭐ |
| **13** | **Weather overlay / conditions** | No weather data associated with tracks (temperature, wind, precipitation). Could retroactively fetch from weather APIs. | ⭐⭐⭐ |
| **14** | **Goal setting & progress** | No weekly/monthly/yearly goals (e.g., "run 100km this month") with progress tracking. Garmin/Strava staple. | ⭐⭐⭐ |
| **15** | **Multi-sport / transition support** | No triathlon or multi-sport activity view with transitions between segments. | ⭐⭐⭐ |

### TIER 3 — Medium Impact (polish & engagement)

| # | Feature | What's Missing | Impact |
|---|---------|---------------|--------|
| **16** | **Dashboard / landing page** | Home goes straight to map. No personalized dashboard showing recent activities, weekly summary, upcoming goals, weather. | ⭐⭐⭐ |
| **17** | **Activity feed / timeline** | No chronological activity feed with thumbnails, key stats, and maps — the core Strava home experience. | ⭐⭐⭐ |
| **18** | **Lap / split analysis** | No automatic km/mile split table (pace per km, elevation per km). Standard in running apps. | ⭐⭐⭐ |
| **19** | **Track comparison side-by-side** | Virtual race does multi-track replay, but no static side-by-side comparison of two activities (same route, different days). | ⭐⭐⭐ |
| **20** | **Map 3D / terrain view** | MapLibre GL supports 3D terrain rendering with `terrain` source. DEM tiles are already referenced for hillshading — just not enabled for 3D. | ⭐⭐⭐ |
| **21** | **Internationalization (i18n)** | All UI is English-only, dates are Swiss-locale (de-CH). No language selector or translated strings. | ⭐⭐⭐ |
| **22** | **Notifications / alerts** | No push notifications for new imports, weekly summaries, or goal completions. PWA supports this. | ⭐⭐⭐ |
| **23** | **Photo timeline in track detail** | Media is shown as map clusters, but not integrated into the track detail timeline (show photos at their timestamp position along the elevation chart). | ⭐⭐⭐ |

### TIER 4 — Lower Impact (nice-to-have, forward-looking)

| # | Feature | What's Missing | Impact |
|---|---------|---------------|--------|
| **24** | **Social / sharing** | No share link, public profile, or social features. Even for personal use, shareable track links are useful. | ⭐⭐ |
| **25** | **Gear / equipment tracking** | No way to assign shoes, bikes, etc. to activities and track total mileage per gear item (retire shoes at 800km). | ⭐⭐ |
| **26** | **Tags / labels on tracks** | Beyond filters, no free-form tagging (e.g., "with kids", "race day", "rainy"). | ⭐⭐ |
| **27** | **GPX import from client** | Import is backend-only (Garmin export / file watcher). No drag-and-drop GPX upload from the browser. | ⭐⭐ |
| **28** | **Keyboard shortcuts** | No keyboard navigation (J/K for prev/next track, ESC to close panels, Space for play/pause animation). | ⭐⭐ |
| **29** | **Strava / Komoot API sync** | Only Garmin import. No Strava or Komoot OAuth connection for automatic sync. | ⭐⭐ |
| **30** | **Offline track recording** | PWA could record tracks offline and sync when back online. | ⭐⭐ |
| **31** | **Surface type detection** | No trail vs road vs gravel classification. Could be inferred from OSM data along the track. | ⭐ |
| **32** | **Accessibility (a11y)** | No ARIA labels, keyboard focus management, or screen reader support observed. | ⭐ |

---

## Quick Wins (low effort, noticeable improvement)

These can be implemented relatively quickly given existing infrastructure:

1. **Heatmap layer** (#8) — MapLibre has `heatmap` layer type; all track coordinates are already loaded
2. **Export GPX** (#11) — Track data is available; just need a download button + GPX XML builder
3. **Km splits table** (#18) — Data points have `distanceSinceStartInMeters`; just bucket by km
4. **3D terrain** (#20) — Terrarium DEM tiles are already referenced for hillshading; MapLibre `terrain` enables 3D
5. **Photo timeline integration** (#23) — Media has timestamps, charts have `syncClick`; connect them
6. **Keyboard shortcuts** (#28) — Small event listeners on existing actions

---

## Biggest Impact for Effort

The combination of **Personal Records** (#3) + **Heatmap** (#8) + **Yearly Summary** (#9) would make the app feel dramatically more engaging without requiring massive architectural changes — all the underlying data already exists.
