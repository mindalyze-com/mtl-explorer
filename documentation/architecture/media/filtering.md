# Media filtering

The filter result defines the media time range in MTL Explorer.

1. Resolve the actual matching track IDs, including for custom filters.
2. On the server, take the earliest valid start and latest valid end of those tracks.
3. Include media whose capture time falls within that continuous interval, including both endpoints.

Filter names, date fields, weeks, activity categories and SQL parameters are never interpreted to infer a second time filter. Track visibility and map colors do not alter the result. Tracks without a finite, complete start/end interval, or with an end before their start, cannot define the range. No matching dated tracks means no media.

For example, if matching tracks run from September 5 at 09:00 through September 17 at 18:00, that is the media interval. A photo on September 10 is included even if no track exists that day. A photo on September 4 is excluded. Selecting a nominal two-week period can therefore yield a narrower media interval, depending on the actual matching tracks. An activity-type filter can yield a range spanning years.

## Related and unrelated media

- **All media** is the default. It includes linked and unlinked photos and videos inside the interval.
- **Track related** adds a requirement that the selected media correlation links to a matching track. The time interval remains the same.

Both choices use the media’s own capture time: EXIF GPS time first, otherwise EXIF camera time with any saved camera correction. A track link cannot bring older, newer or undated media into the interval. File creation and import dates are not substitutes. Media without a usable capture date is excluded.

## Consistent views

The main map, Overview media count, Trends chart/table and period mosaic use the same selection. The map additionally requires a location inside the loaded viewport, so its visible marker count can be smaller than the statistics count. Activity and media charts share aligned periods, with zero values where a series has no items.

Choose All media or Track related at the top of Trends or in Map settings → Your data. The choice applies to both the map and statistics for the current session. Filter or scope changes clear old map markers and refresh media results; stale requests cannot restore the old selection.

An explicitly opened activity’s own media timeline remains an activity detail view. Health retains its separate data range.

## Implementation

`MediaTrendQueryRepository` owns the track interval and media capture-time predicate used by bounds, trend and item queries. All three APIs require matching `trackIds` in both scopes. `ALL_INDEXED` remains the API enum value for the All media choice; it now means all media within the result-defined interval. The map bounds endpoint uses POST to carry large track selections. An empty ID list returns an empty result; a missing list is invalid.

Synthetic database integration tests cover continuous gaps, inclusive boundaries, old/future/undated media, selected links, capture-time corrections, empty selections, and agreement between bounds, counts and browsing. Frontend tests cover ID forwarding, shared scope, aligned charts, totals, and stale request cancellation.
