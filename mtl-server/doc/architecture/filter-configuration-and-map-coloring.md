# Filter Configuration And Map Coloring

This document describes how track filters are configured, persisted, exposed to
the UI, and used for map coloring and legends.

The important distinction is:

- A filter decides which tracks are returned.
- A filter can optionally assign each returned track to a group.
- Metadata on the filter tells the UI how to organize the filter, sort groups,
  label groups, and choose colors.

## 1. Filter Storage

Filters are stored in `filter_config`.

Core identity and execution fields:

| Column | Description |
|---|---|
| `filter_domain` | Domain the filter applies to. Currently `GPS_TRACK`. |
| `filter_name` | Stable unique filter key within the domain. |
| `filter_type` | Execution type. Currently `SQL`. |
| `filter_category` | `SYSTEM` or `USER`. |
| `expression` | SQL template used to resolve matching track IDs and optional groups. |
| `display_name` | Human-facing filter name. |
| `description` | Human-facing explanation and use case. |
| `display_order` | Ordering within filter lists. The frontend also uses the lowest order in a group to order selector groups. |

UI and coloring metadata:

| Column | Description |
|---|---|
| `filter_group` | UI section for the filter selector, for example `Date & Time`, `Performance`, or `People`. |
| `group_semantics` | What the returned group values mean. |
| `coloring_strategy` | How colors should be assigned to groups. |
| `legend_sort_strategy` | How the map and preview legends should be ordered. |
| `preferred_palette` | Optional default palette key used when the user has not chosen a palette yet. |
| `group_label_template` | Optional display template for raw group values. |
| `ui_metadata` | Optional JSON metadata for UI hints, formatting, and optional params. |

The audit table `filter_config_audit` mirrors these fields. The audit trigger
must be updated whenever filter metadata columns are added.

## 2. Typed Metadata Values

The database stores metadata as text columns, but Java models fixed metadata
sets as enums with `@Enumerated(EnumType.STRING)`.

Source of truth:

`FilterConfigEntity`

### `GROUP_SEMANTICS`

| Value | Meaning |
|---|---|
| `CATEGORICAL` | Unordered names such as activity type, quality status, or person. |
| `DATE_BUCKET` | Date-like buckets such as year or calendar day. |
| `ORDINAL` | Ordered labels with a fixed known sequence, such as quarters or weekdays. |
| `NUMERIC_BUCKET` | Numeric buckets, usually percentile-style values. |

### `COLORING_STRATEGY`

| Value | Meaning |
|---|---|
| `CATEGORICAL` | Assign distinct colors per group. Encounter order should not matter after legend sorting. |
| `SEQUENTIAL_GRADIENT` | Map ordered numeric group values onto a continuous palette. |

### `LEGEND_SORT_STRATEGY`

| Value | Meaning |
|---|---|
| `LABEL_ASC` | Sort labels alphabetically, with numeric-aware comparison. |
| `NUMERIC_ASC` | Sort numeric bucket values from low to high. |
| `COUNT_DESC` | Sort groups by matching track count, largest first. |

These dimensions are intentionally separate. For example, an ordered weekday
filter has `group_semantics = ORDINAL` but still uses categorical colors. A
speed gradient filter has `group_semantics = NUMERIC_BUCKET` and
`coloring_strategy = SEQUENTIAL_GRADIENT`.

## 3. SQL Filter Contract

A SQL filter returns at least:

```
id
```

Grouped/coloring filters also return:

```
grp
```

The raw `grp` value is the stable group key used by the map layer and by group
visibility toggles. The UI can display a friendlier label using metadata, but
the raw key should remain stable.

Most GPS track filters include the standard base filter:

```
[[~{/GPS_TRACK/SmartBaseFilter}]]
```

This keeps normal date, geo area, successful-load, duplicate, and planned-track
constraints consistent across filters.

## 4. Filter Groups

Current top-level UI groups are:

| Group | Display-order range | Typical filters |
|---|---:|---|
| `Core` | `1000-1999` | Standard base filter. |
| `Activity` | `2000-2999` | Activity type and motorized/non-motorized filters. |
| `Date & Time` | `3000-3999` | Year, day, quarter, weekday, time of day. |
| `People` | `4000-4999` | Personal YAML-defined companion filters. |
| `Performance` | `5000-5999` | Average speed, distance, elevation gain, energy gradients. |
| `Quality` | `6000-6999` | Suspicious, duplicate, and error filters. |
| `User` | `9000-9899` | User-defined filters without a more specific group. |
| `Other` | `9900+` | Fallback for uncategorized filters. |

The frontend orders selector groups by the lowest `display_order` inside each
group, then uses a small known-group fallback order only for ties or missing
orders. This keeps the database metadata as the primary source of ordering
truth while still giving unknown group names stable behavior.

## 5. Presentation Defaults

`preferred_palette` describes the recommended first palette for a filter.
Legend order is a client-side choice. The frontend stores user overrides in the
client filter configuration, so changing legend order or palette does not mutate
the system filter definition.

If `legend_sort_strategy` is missing, the frontend preserves the first-seen
group order returned by the SQL query. Filters that need a specific default
order should make that order explicit in SQL. Numeric ordering is only used when
`NUMERIC_ASC` is selected by the user.

The frontend uses `preferred_palette` when a filter is selected and no palette
is active yet. It also replaces an obviously incompatible palette when switching
between categorical and gradient filters:

- gradient filter with categorical palette: switch to the filter's preferred
  gradient palette
- categorical filter with gradient palette: switch to the filter's preferred
  categorical palette
- categorical filter with an existing categorical palette: keep the user's
  current choice

Current categorical defaults use existing palette keys:

| Filter shape | Preferred palette |
|---|---|
| naturally small groups, such as quarters, motorized/non-motorized, quality filters | `005_COLORS` |
| weekday, time-of-day, activity, people, and general user filters | `008_COLORS` |
| standard base filter without groups | none |

`ui_metadata.optionalParams` lists params that may be left blank even when they
are rendered as editable fields, for example `DATE_TIME_FROM`,
`DATE_TIME_TO`, or gradient min/max range params.

## 6. Gradient Filters

Gradient filters are designed for ordered numeric buckets.

Current gradient filters:

| Filter | Metric | Unit | Params |
|---|---|---|---|
| `TracksByAverageSpeedGradient` | Average moving speed | `km/h` | `AVG_SPEED_MIN_KMH`, `AVG_SPEED_MAX_KMH` |
| `TracksByDistanceGradient` | Track distance | `km` | `DISTANCE_MIN_KM`, `DISTANCE_MAX_KM` |
| `TracksByElevationGainGradient` | Elevation gain | `m` | `ELEVATION_GAIN_MIN_M`, `ELEVATION_GAIN_MAX_M` |
| `TracksByEnergyGradient` | Estimated net mechanical energy | `kWh` | `ENERGY_MIN_KWH`, `ENERGY_MAX_KWH` |

Each gradient filter assigns the current result set to bucket values `0` to
`99`. The bucket is based on sorted rank within the filtered result set, not on
an absolute metric threshold. This keeps the full color range useful for the
visible tracks.

For small result sets, the SQL scales rank positions across the full `0-99`
range. With two tracks, the lower track gets bucket `0` and the higher track
gets bucket `99`. With one track, the bucket is `0`.

Recommended metadata for gradient filters:

| Field | Value |
|---|---|
| `group_semantics` | `NUMERIC_BUCKET` |
| `coloring_strategy` | `SEQUENTIAL_GRADIENT` |
| `preferred_palette` | A `GRADIENT_100_*` palette, usually `GRADIENT_100_VIRIDIS`. |
| `group_label_template` | A metric-specific label such as `Avg speed percentile {bucket}`. |

The frontend maps bucket values to palette indices. A 100-color palette maps
bucket `0` to the first color and bucket `99` to the last color. Shorter
palettes are scaled proportionally.

## 7. Group Labels

The raw group key should remain compact and stable. Display labels are derived
in the UI.

For numeric buckets, `group_label_template` supports:

| Placeholder | Meaning |
|---|---|
| `{bucket}` | Zero-padded bucket based on the configured bucket count. For 100 buckets this is `00` to `99`. |
| `{bucketNumber}` | Raw numeric bucket without padding. |

Example:

```
group_label_template = "Avg speed percentile {bucket}"
grp = "1"
display label = "Avg speed percentile 01"
```

This keeps legends readable while preserving numeric sorting.

## 8. YAML-Defined Filters

Filters can also be supplied through application YAML under:

```
mtl.filter-configs
```

Example metadata:

```yaml
filter-group: People
group-semantics: CATEGORICAL
coloring-strategy: CATEGORICAL
legend-sort-strategy: LABEL_ASC
preferred-palette: 008_COLORS
```

The YAML properties bind directly to `FilterConfigEntity`, so enum values must
match the Java enum names exactly.

The initializer only creates YAML filters that do not already exist in the
database. If metadata changes must apply to existing rows, add a Liquibase
backfill changeSet as well.

## 9. OpenAPI And Frontend Types

`FilterConfigEntity` is exposed through the OpenAPI schema. Because the metadata
fields are Java enums, the generated TypeScript client exposes matching enum
types.

Frontend code should use the generated enum constants instead of duplicating raw
strings. This keeps UI behavior aligned with the server contract.

Relevant frontend behavior:

- the filter selector groups filters by `filter_group`
- the map legend and preview legend sort by the user override, otherwise
  preserve the first-seen group order returned by SQL
- gradient filters use `coloring_strategy = SEQUENTIAL_GRADIENT`
- display labels use `group_label_template`
- gradient help text is based on `ui_metadata`

## 10. Key Source Files

| File | Role |
|---|---|
| `mtl-server/src/main/java/.../db/entity/config/FilterConfigEntity.java` | JPA entity and metadata enums. |
| `mtl-server/src/main/java/.../logic/grouping/sql/custom/FilterConfigInitializer.java` | Creates YAML-defined filters if missing. |
| `mtl-server/src/main/resources/db/changelog/changes/047.xml` | Metadata columns, audit trigger update, backfills, gradient SQL updates. |
| `mtl-server/src/main/resources/application-patrick.yml` | Example YAML-defined user filters with metadata. |
| `mtl-client/src/utils/filterMetadata.ts` | Frontend metadata interpretation, label formatting, legend sorting, gradient color mapping. |
| `mtl-client/src/components/filter/CustomFilter.vue` | Filter selector, preview legend, palette guidance. |
| `mtl-client/src/components/map/Map.vue` | Map color assignment and map legend entries. |
