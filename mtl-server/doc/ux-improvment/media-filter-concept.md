# Media Filter Concept

Status: proposal

This document proposes a separate advanced filter domain for indexed photos and
videos. It extends the existing filter model without requiring media to be
linked to GPS tracks.

Related documentation:

- [Filter Configuration And Map Coloring](filter-configuration-and-map-coloring.md)
- [Photo handling improvement](../../../documentation/photo-handling-improvement.md)

## 1. Decisions

- Keep `GPS_TRACK` and `MEDIA` as separate filter domains.
- Show both domains as tabs inside the existing **Filter** tool.
- Store, enable, pause, and reset each filter independently.
- Apply the active media filter to all media collections in MTL Explorer:
  map markers, clusters, statistics, media review, viewer navigation, and the
  activity Photos tab.
- Provide **Related to current tracks** as an explicit bridge to the current
  track-filter result.
- Provide an optional **Use track date and area** link. Other media criteria
  remain independent.
- Reuse the complete advanced-filter model: views, criteria, result categories,
  map colors, and review.
- Do not require a track relationship. Unlinked media remains a normal part of
  the media domain.

## 2. User Model

The Filter sheet contains two tabs:

```text
[ Tracks · 126 ]  [ Photos & media · 2,480 ]
```

Each tab owns its selected view, parameters, included result groups, palette,
enabled state, and result count.

The media domain can provide criteria for:

- capture date and geographic area;
- image or video;
- camera make or model;
- known or unknown capture time;
- known or unknown position;
- position origin;
- related, unrelated, or ambiguously related media;
- relation to the current track-filter result;
- related activity type.

When **Use track date and area** is enabled, the media filter uses the track
filter's effective base date and area values. Media-specific criteria continue
to apply. An unlinked photo can therefore match the same date and area without
claiming an activity relationship.

When a media filter affects an activity Photos tab, the tab must state the
intersection explicitly, for example `4 of 17 match the media filter`. An empty
result must not claim that the activity has no media.

## 3. SQL Contract

Add `MEDIA` to `FilterConfigEntity.FILTER_DOMAIN`. Media filters follow the
existing SQL result contract:

```text
id
grp  (optional)
```

`id` is a media ID within the `MEDIA` domain. `grp` is the stable raw group key
used for categories, legends, and colors.

Most media views should include a common base filter:

```text
[[~{/MEDIA/SmartMediaBaseFilter}]]
```

Track and media IDs must remain typed by their domain. Do not combine them into
one unqualified ID collection.

## 4. Media Filter Read View

Introduce one stable read view so filter definitions do not duplicate capture
time, media-kind, position, and correlation rules.

```sql
CREATE OR REPLACE VIEW media_filter_v AS
SELECT
    media.id,
    indexed.name AS file_name,
    indexed.path AS file_path,

    CASE
        WHEN LOWER(indexed.name) ~ '[.](3gp|avi|m4v|mkv|mov|mp4)$'
            THEN 'VIDEO'
        ELSE 'IMAGE'
    END AS media_kind,

    COALESCE(
        media.exif_gps_date,
        media.exif_date_image_taken
            + make_interval(
                secs => COALESCE(correction.offset_seconds, 0)
            )
    ) AS capture_time,

    media.camera_make,
    media.camera_model,

    COALESCE(
        resolved.location,
        media.exif_gps_location
    ) AS resolved_location,

    COALESCE(
        resolved.position_origin,
        CASE
            WHEN media.exif_gps_location IS NOT NULL
                THEN 'EXIF_EMBEDDED'
            ELSE 'UNKNOWN'
        END
    ) AS position_origin,

    correlation.track_id AS matched_track_id,
    track.activity_type AS matched_activity_type,
    COALESCE(correlation.ambiguous, FALSE) AS ambiguous_match,
    COALESCE(correlation.alternative_count, 0) AS alternative_match_count

FROM media_file media

JOIN indexed_file indexed
    ON indexed.id = media.file_id

LEFT JOIN media_time_correction correction
    ON correction.media_id = media.id

LEFT JOIN media_resolved_location resolved
    ON resolved.media_id = media.id

LEFT JOIN media_track_correlation correlation
    ON correlation.media_id = media.id
   AND correlation.selected

LEFT JOIN gps_track track
    ON track.id = correlation.track_id;
```

The view preserves current semantics:

- EXIF GPS time is authoritative.
- Saved camera-time correction applies only to camera time.
- Resolved position keeps manual, EXIF, and interpolated precedence.
- EXIF location is a fallback while the resolved projection is pending.
- Only the selected correlation supplies the media-to-track relationship.

Media-kind classification should have one production source of truth. If this
view is implemented, keep its extension list aligned with
`MediaKindResolver`, or move the SQL expression to a shared database function.

## 5. Base Filter Example

`SmartMediaBaseFilter` provides optional media criteria. The first version can
support date, media kind, camera, position origin, and the same three geographic
shape types as the track base filter.

```sql
SELECT
    media.id,
    media.media_kind AS grp

FROM media_filter_v media

WHERE
    (
        :DATE_TIME_FROM IS NULL
        OR :DATE_TIME_FROM = ''
        OR media.capture_time >= TO_TIMESTAMP(
            :DATE_TIME_FROM,
            'YYYY-MM-DD HH24:MI:SS'
        )
    )

    AND (
        :DATE_TIME_TO IS NULL
        OR :DATE_TIME_TO = ''
        OR media.capture_time <= TO_TIMESTAMP(
            :DATE_TIME_TO,
            'YYYY-MM-DD HH24:MI:SS'
        )
    )

    AND (
        NULLIF(BTRIM(:MEDIA_KIND), '') IS NULL
        OR media.media_kind = UPPER(BTRIM(:MEDIA_KIND))
    )

    AND (
        NULLIF(BTRIM(:CAMERA_QUERY), '') IS NULL
        OR CONCAT_WS(
            ' ',
            media.camera_make,
            media.camera_model
        ) ILIKE '%' || BTRIM(:CAMERA_QUERY) || '%'
    )

    AND (
        NULLIF(BTRIM(:POSITION_ORIGIN), '') IS NULL
        OR media.position_origin = UPPER(BTRIM(:POSITION_ORIGIN))
    )

    AND (
        :GEO_CIRCLE_1_POINT IS NULL
        OR (
            media.resolved_location IS NOT NULL
            AND ST_DWithin(
                media.resolved_location::geography,
                ST_GeographyFromText(:GEO_CIRCLE_1_POINT),
                CAST(:GEO_CIRCLE_1_RADIUS AS double precision)
            )
        )
    )

    AND (
        :GEO_RECTANGLE_1_MIN_LAT IS NULL
        OR (
            media.resolved_location IS NOT NULL
            AND ST_Intersects(
                media.resolved_location,
                ST_MakeEnvelope(
                    CAST(:GEO_RECTANGLE_1_MIN_LNG AS double precision),
                    CAST(:GEO_RECTANGLE_1_MIN_LAT AS double precision),
                    CAST(:GEO_RECTANGLE_1_MAX_LNG AS double precision),
                    CAST(:GEO_RECTANGLE_1_MAX_LAT AS double precision),
                    4326
                )
            )
        )
    )

    AND (
        :GEO_POLYGON_1 IS NULL
        OR (
            media.resolved_location IS NOT NULL
            AND ST_Intersects(
                media.resolved_location,
                ST_GeomFromText(:GEO_POLYGON_1, 4326)
            )
        )
    )

ORDER BY
    media.capture_time NULLS LAST,
    media.id;
```

Unlike the track base filter, absent date criteria must retain undated media.
An active date boundary excludes undated media because it cannot satisfy the
boundary.

## 6. View Examples

### Media By Kind

```sql
SELECT
    media.id,
    media.media_kind AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    media.media_kind,
    media.capture_time NULLS LAST,
    media.id;
```

### Media By Position Origin

```sql
SELECT
    media.id,
    CASE media.position_origin
        WHEN 'EXIF_EMBEDDED' THEN 'Photo GPS'
        WHEN 'TRACK_INTERPOLATED' THEN 'Estimated'
        WHEN 'USER_ASSIGNED' THEN 'Set by you'
        ELSE 'Position unknown'
    END AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    media.position_origin,
    media.capture_time NULLS LAST,
    media.id;
```

### Media By Year

```sql
SELECT
    media.id,
    CASE
        WHEN media.capture_time IS NULL THEN 'Undated'
        ELSE TO_CHAR(media.capture_time, 'YYYY')
    END AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    media.capture_time NULLS LAST,
    media.id;
```

### Media By Camera

```sql
SELECT
    media.id,
    COALESCE(
        NULLIF(
            BTRIM(
                CONCAT_WS(
                    ' ',
                    media.camera_make,
                    media.camera_model
                )
            ),
            ''
        ),
        'Unknown camera'
    ) AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    grp,
    media.capture_time NULLS LAST,
    media.id;
```

### Media By Track Relationship

```sql
SELECT
    media.id,
    CASE
        WHEN media.matched_track_id IS NULL THEN 'Unrelated'
        WHEN media.ambiguous_match THEN 'Ambiguous'
        ELSE 'Related'
    END AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    grp,
    media.capture_time NULLS LAST,
    media.id;
```

### Media By Related Activity Type

```sql
SELECT
    media.id,
    COALESCE(
        media.matched_activity_type,
        'Unrelated'
    ) AS grp

FROM media_filter_v media

WHERE media.id = ANY (
    SELECT id
    FROM (
        [[~{/MEDIA/SmartMediaBaseFilter}]]
    ) base_filter
)

ORDER BY
    grp,
    media.capture_time NULLS LAST,
    media.id;
```

## 7. Current Track Result Bridge

The media relationship criterion has four values:

| Value | Meaning |
|---|---|
| `ANY` | Do not constrain media by track relationship. |
| `RELATED` | Require a selected track correlation. |
| `UNRELATED` | Require no selected track correlation. |
| `CURRENT_TRACKS` | Require the selected correlation to reference a track in the current track-filter result. |

A prototype can supply the current track IDs as the existing string parameter
shape:

```sql
SELECT
    media.id,
    media.media_kind AS grp

FROM media_filter_v media

WHERE
    media.matched_track_id = ANY (
        ARRAY(
            SELECT token::bigint
            FROM regexp_split_to_table(
                COALESCE(:TRACK_IDS, ''),
                '[,;[:space:]]+'
            ) AS token
            WHERE token ~ '^[0-9]+$'
        )
    )

    AND media.id = ANY (
        SELECT id
        FROM (
            [[~{/MEDIA/SmartMediaBaseFilter}]]
        ) base_filter
    )

ORDER BY
    media.capture_time NULLS LAST,
    media.id;
```

Do not repeatedly send large track-ID collections with map-bound requests. The
production API should resolve or reference the active track-filter context on
the server and apply the same relationship predicate there.

Alternative, non-selected ambiguous correlations do not satisfy this bridge.
The meaning stays consistent with activity timelines and media trends.

## 8. Server And API Changes

Implementation requires more than adding filter rows:

1. Add `MEDIA` to the filter-domain enum and make template resolution accept
   the domain.
2. Separate generic filter execution from GPS-track enrichment. Track versions,
   track entities, and the standard track count must remain track-specific.
3. Define a media filter result with media count, group assignments, group
   summaries, and a server-side result context suitable for large libraries.
4. Make media map bounds, statistics, review, viewer navigation, and activity
   timeline reads accept the active media-filter context.
5. Make the optional shared date and area scope explicit in the request model.
   Do not infer it by parsing arbitrary SQL.
6. Update the server API first, save the live OpenAPI schema, and regenerate the
   TypeScript client from it.

The browser should not receive every matching media ID for a large library.
Advanced media calculations and result composition remain server-owned.

## 9. Initial System Views

The initial `MEDIA` catalog should contain:

1. `SmartMediaBaseFilter`
2. `MediaByKind`
3. `MediaByPositionOrigin`
4. `MediaByYear`
5. `MediaByCamera`
6. `MediaByTrackRelationship`
7. `MediaByRelatedActivityType`

Use existing group semantics, coloring strategies, palette metadata, group
selection, and UI metadata where their meanings are unchanged.

## 10. Validation

Cover at least these cases:

- the default media filter returns all indexed media, including undated and
  unlocated items;
- each active date boundary excludes undated media without changing the default;
- circle, rectangle, and polygon criteria use resolved positions;
- manual, EXIF, interpolated, and unknown positions receive the correct group;
- images and supported video extensions match `MediaKindResolver`;
- camera grouping handles missing make and model;
- related, unrelated, and ambiguous media receive distinct groups;
- `CURRENT_TRACKS` follows the final track result after category selection;
- an empty current track result produces no `CURRENT_TRACKS` media;
- changing or pausing one filter does not reset the other filter;
- the media filter is applied consistently to every media collection;
- map reads remain bounded and indexed for large media libraries;
- failures in one domain leave the last successful result from the other domain
  usable.

This proposal does not require legacy-data backfill. Existing indexing and
correlation jobs populate the source tables used by the read view.
