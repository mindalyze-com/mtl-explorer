# Media correlation

MTL Explorer places indexed photos and videos in activity context without
changing their source metadata. Track Details includes a **Media** tab with a
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

Timeline and trend-item responses carry `positionOrigin`, shown as **Set by you**,
**Photo GPS**, or **Estimated**. Clearing a user assignment exposes the
preserved EXIF or track-derived position again. A track-derived position never
creates EXIF GPS metadata. If provenance is unavailable, the client shows
**Position unknown** instead of assuming embedded GPS.

## Capture time and activity matching

EXIF GPS time is authoritative when present. Otherwise, EXIF
`DateTimeOriginal` is used with an optional saved camera offset from `-24` to
`+24` hours. Corrections are stored separately in `media_time_correction` and
never modify source timestamps.

Only successful, unique, imported activities with supported recorded timing
and timestamped canonical points are eligible. Estimated or unsupported timing
cannot supply time-based matches. Map/statistics use decisions do not erase an
otherwise supported correlation; selection still controls map and trend scope.
All overlapping activity correlations are retained. The selected
match is deterministic: a photo with GPS prefers the nearest route; remaining
ties use interval midpoint, duration, then activity id. The response includes
`ambiguousMatch` and `alternativeMatchCount` when more than one activity was
eligible. Map-point responses contain coordinates only; the ordinary media-details
response does not carry this provenance. A viewer needs the collection item's
provenance to label its position.

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


## API

The OpenAPI schema is the source of truth and the frontend uses its generated
TypeScript client.

- `GET /mtl/api/media/by-track/{trackId}` returns a page envelope when
  `cameraOffsetSeconds=0`; a non-zero offset returns the corresponding unsaved
  preview page. `page` is zero-based, `pageSize` defaults to 100 and is limited
  to 200, and the response includes total item and page counts.
- `POST /mtl/api/media/trends` returns grouped photo/video counts within
  the matching tracks' continuous time range. Track related also requires a
  selected correlation to a matching track. See [selection semantics](filtering.md).
- `POST /mtl/api/media/trends/items` returns a stable newest-first page for one
  trend period and optional photo/video kind filter. The UI requests 60 items
  per page.
- `PUT /mtl/api/media/time-corrections` saves or clears reversible corrections
  for a bounded media-id set and returns `204 No Content`.
- `PUT /mtl/api/media/{mediaId}/manual-location` saves a user assignment.
- `DELETE /mtl/api/media/{mediaId}/manual-location` clears it.

A missing activity or media item returns `404`. Invalid coordinates, offsets,
or request sizes return `400`.

See [the media guide](../../user/media/README.md) for browsing and corrections.
