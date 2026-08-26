# Photo handling improvement

MTL Explorer places indexed photos and videos in activity context without
changing their source metadata. Track Details includes a **Photos** tab with a
persisted timeline, position provenance, camera-clock correction, manual
location assignment, and the shared media viewer.

## Position evidence and resolution

Each source is stored separately so later information does not overwrite
earlier evidence:

- `media_file.exif_gps_location` keeps the embedded EXIF point unchanged.
- `media_track_correlation` keeps every eligible activity correlation and its
  interpolated route point.
- `media_manual_location` keeps the point and note entered by the user.
- `media_resolved_location` is the small indexed projection used by map reads.

The resolved position uses this precedence:

1. `USER_ASSIGNED`
2. `EXIF_EMBEDDED`
3. `TRACK_INTERPOLATED`

`positionOrigin` is returned to the client and shown as **Set by you**,
**Photo GPS**, or **Estimated**. Clearing a user assignment exposes the
preserved EXIF or track-derived position again. A track-derived position never
creates EXIF GPS metadata. If provenance is unavailable, the client shows
**Position unknown** instead of assuming embedded GPS.

## Capture time and activity matching

EXIF GPS time is authoritative when present. Otherwise, EXIF
`DateTimeOriginal` is used with an optional saved camera offset from `-24` to
`+24` hours. Corrections are stored separately in `media_time_correction` and
never modify source timestamps.

Only successful, unique, imported activities with timestamped canonical points
are eligible. All overlapping activity correlations are retained. The selected
match is deterministic: a photo with GPS prefers the nearest route; remaining
ties use interval midpoint, duration, then activity id. The response includes
`ambiguousMatch` and `alternativeMatchCount` when more than one activity was
eligible.

Track position is interpolated between the canonical points around the adjusted
capture time. The stored correlation includes route position, distance,
duration, nearest point index and time delta, algorithm version, activity
version, applied offset, and calculation time.

## Incremental correlation job

Correlation is asynchronous and durable. Media metadata changes, saved time or
manual-location changes, activity changes, and authoritative
`RAW_OUTLIER_CLEANED` track-data changes add small work items. Track work first
expands to media in the activity's old or new indexed time window. Media work is
then processed in bounded batches. A failed batch is retried one item at a
time. Deterministic failures are deferred with an error summary so later work
continues instead of repeatedly blocking the queue.

This avoids comparing every photo with every activity. Replacing or deleting an
activity also requeues its previously correlated photos so they can fall back to
another match, EXIF, or no resolved position. An algorithm-version marker allows
stale rows to be recalculated after the matching rules change.

Reads keep the last completed result while recalculation is pending. Admin job
status reports the remaining correlation work.

Normal read paths do no interpolation:

- map bounds query the GiST-indexed `media_resolved_location` projection;
- an activity timeline queries a bounded page through the
  `(track_id, adjusted_capture_time, media_id)` selected-correlation index;
- the original media content remains loaded only when a thumbnail or viewer
  requests it.

## Map media navigation

Selecting any map media marker first opens a collection chooser. A single
marker offers **This photo** or **This location** when photos overlap. A cluster
offers **This cluster**. **Current map view** is a separate choice for all
positioned photos visible when the marker was clicked. If GPS activities pass
the clicked location, **Photos along a GPS track** opens the only match directly.
With several matches, it uses the standard map track chooser and opens the
selected activity at its Photos tab.

The viewer title and filmstrip keep the chosen scope visible. Cluster navigation
starts with a bounded page and loads another bounded page only at a navigation
boundary. The map camera and zoom remain unchanged while choosing or opening a
collection.

## Statistics media trends

The final chart in **Statistics → Trends** shows stacked photo and video counts.
It follows the selected total, year, quarter, month, week, or day grouping and
defaults to **All indexed**, where every dated indexed item can define the chart
range. **Track related** limits the chart to media linked to activities in the
current track filters. Each scope includes a short visible explanation and a
hover/focus tooltip.

The Trends table keeps its full period list and shows separate photo and video
counts. Changing the media scope does not remove activity rows.

Media and activity charts share one dated timeline. A period missing from one
source is kept with a zero value, so every chart stays aligned. Undated indexed
media is shown as a separate compact drill-down instead of adding a false time
period to the axes.

Selecting a non-empty media stack opens a paged mosaic for that period. The
mosaic can show all media, photos only, or videos only; loads 60 items at a
time; opens the shared viewer; and, in Track related scope, can return directly
to the linked activity's Photos tab.

## Track Details and viewer

The Photos tab lazy-loads the selected activity timeline in server-backed pages
of 100 items by default. The list and mini-map render only the current page;
page sizes are limited to 200. Each row shows capture time, route distance,
saved clock correction, position origin, preview state, and ambiguity where
applicable. Camera metadata remains in the viewer Details panel. Mini-map
markers keep one circular camera symbol and use
color for Photo GPS, Estimated, Set by you, and unknown positions.

The timeline is content-first: **Photo tools** is collapsed by default and
contains the uncommon camera-clock and location-correction workflows. An
active preview or correction is signaled on the disclosure. Entering a camera
offset and choosing **Preview** performs an unsaved indexed
comparison for that activity and page. **Save correction** persists it for
camera-time photos found in either the preview or the same page of the current
persisted activity timeline. This union also updates page items shifted out of
the activity. **Reset** discards the preview. A saved correction can be cleared
later.

Choosing **Adjust locations** inside Photo tools reveals the per-photo
**Set location** actions. A location stores a display position and optional
note. The editor states that original EXIF and activity correlation data remain
preserved. Leaving the mode hides the actions; clearing an assignment restores
normal precedence.

The viewer uses a photo/details split on desktop. On phones, it opens photo
first and exposes the same information in a scrollable details sheet. It
supports:

- large side-mounted previous and next buttons, left/right arrow keys, swipe,
  and a dark dock that combines the filename with a scoped photo filmstrip;
- a filmstrip control that states its current collection, such as **On this
  map** or **In this cluster**, and collapses to one compact dark row while
  preserving the filename and current count. The strip renders a sliding
  window of at most 200 thumbnails and loads the adjacent map-cluster or
  activity page when navigation reaches either boundary;
- mouse-wheel, double-click, and pinch zoom from `1x` to `6x`;
- bounded panning while zoomed and a reset control;
- structured metadata including capture date, time, timestamp source and
  position origin; a lower-right desktop location map that fits the selected
  activity or retains the previous main-map overview and supports standard
  zoom controls; an action to
  return to the main map, native momentum scrolling for the phone details
  sheet, and original download;
- viewer chrome and details surfaces default to dark. A header shortcut switches
  the complete viewer between dark and light without changing the MTL Explorer
  application theme. The viewer-only choice persists for map, activity and
  statistics photo viewers;
- separate window controls in the sheet header: maximize fills the MTL Explorer
  viewport, while fullscreen uses browser fullscreen with a complete-viewport
  fallback. The window modes are mutually exclusive, so leaving fullscreen
  always returns to the normal sheet instead of a still-maximized panel. Both
  retain the current Details and Nearby states;
- Details and the Nearby filmstrip can be shown or hidden independently before
  or during either window mode. The same fullscreen control or Escape exits
  browser fullscreen without closing the viewer;
- the photo remains reserved for zoom and pan instead of acting as an invisible
  exit zone; side controls and arrow keys remain dedicated to navigation;
- image, video, HEIC conversion, loading, and recoverable error states.

## API

The OpenAPI schema is the source of truth and the frontend uses its generated
TypeScript client.

- `GET /mtl/api/media/by-track/{trackId}` returns a page envelope when
  `cameraOffsetSeconds=0`; a non-zero offset returns the corresponding unsaved
  preview page. `page` is zero-based, `pageSize` defaults to 100 and is limited
  to 200, and the response includes total item and page counts.
- `POST /mtl/api/media/trends` returns grouped photo/video counts for all
  indexed media or the supplied Track related activity IDs.
- `POST /mtl/api/media/trends/items` returns a stable newest-first page for one
  trend period and optional photo/video kind filter. The UI requests 60 items
  per page.
- `PUT /mtl/api/media/time-corrections` saves or clears reversible corrections
  for a bounded media-id set and returns `204 No Content`.
- `PUT /mtl/api/media/{mediaId}/manual-location` saves a user assignment.
- `DELETE /mtl/api/media/{mediaId}/manual-location` clears it.

A missing activity or media item returns `404`. Invalid coordinates, offsets,
or request sizes return `400`.
