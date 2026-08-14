# Measurement Systems And API Units

This document defines metric and US customary presentation in MTL Explorer.

The API uses one canonical unit contract. The client converts canonical values
for display and converts user input back to canonical units. The server receives
the measurement system only when it changes domain meaning, such as the
definition of a milestone.

## 1. Decision

- Persistence and domain calculations use canonical metric units.
- API fields use canonical values and explicit unit suffixes.
- The client owns display and input conversion.
- The server owns measurement-system-dependent definitions and queries.
- Locale and measurement system remain independent.

Converting `100 km` to `62.14 mi` is presentation. Finding the first track of
at least `100 mi` is a different query. The client may do the first operation
but must never derive the second from a metric result.

This design follows the same broad contract style as activity APIs that expose
canonical measurements and keep display preferences separate.

## 2. Measurement Systems

The supported systems are:

| API value | UI label | Long distance | Short distance | Elevation | Speed | Vertical rate | Mass |
|---|---|---|---|---|---|---|---|
| `METRIC` | Metric | `km` | `m` | `m` | `km/h` | `m/h` | `kg` |
| `US_CUSTOMARY` | Imperial (US) | `mi` | `ft` | `ft` | `mph` | `ft/h` | `lb` |

Watts, watt-hours, time, percentages, coordinates, and other
measurement-system-independent values do not change.

The browser persists the selected system independently of locale and map
settings. First-visit selection follows this order:

1. an explicit browser preference
2. `defaultMeasurementSystem` supplied by the server
3. browser locale and timezone detection
4. `METRIC`

Only an explicit user selection is persisted. An automatically resolved value
remains derived so a later server-default change can take effect. The user can
remove the explicit preference with **Use default**, which reruns the selection
order above. `Use default` is a UI action, not a third `MeasurementSystem` enum
value.

## 3. Canonical Units

The canonical API and storage units are:

| Dimension | Canonical unit | Field suffix |
|---|---|---|
| Distance and elevation | meter | `M` |
| Speed | kilometer per hour | `Kmh` |
| Vertical rate | meter per hour | `MPerH` |
| Mass | kilogram | `Kg` |
| Power | watt | `Watts` |
| Energy | watt-hour | `Wh` |
| Duration | second or millisecond | `S`, `Sec`, or `Ms` |

Use named exact conversion constants:

| Constant | Exact value |
|---|---:|
| `METERS_PER_KILOMETER` | `1000` |
| `METERS_PER_MILE` | `1609.344` |
| `METERS_PER_FOOT` | `0.3048` |
| `KILOMETERS_PER_MILE` | `1.609344` |
| `KILOGRAMS_PER_POUND` | `0.45359237` |

Do not store converted copies. Do not rename canonical fields to unit-neutral
names such as `distance`; explicit suffixes are part of the contract.

## 4. Request And Response Contract

Ordinary measurement APIs do not accept `measurementSystem`. They always read
and write canonical values. Examples are `distanceM`, `radiusM`, `speedKmh`,
and `riderWeightKg`.

Only an operation whose result can change semantically accepts:

```text
measurementSystem=METRIC|US_CUSTOMARY
```

The client sends it explicitly. Omission defaults to `METRIC` for API
compatibility. Invalid values are rejected.

Canonical responses do not contain a response-level `meta.units` block and do
not repeat a unit beside each value. Existing list responses remain lists when
no other response structure is needed.

The selected system is returned when it identifies which semantic definition
set was evaluated:

```json
{
  "measurementSystem": "US_CUSTOMARY",
  "summary": {
    "distanceM": 200000,
    "ascentM": 1500,
    "energyWh": 1800
  },
  "milestones": [
    {
      "type": "DISTANCE",
      "thresholdM": 160934.4,
      "trackId": 42,
      "achievedDistanceM": 164999.2
    }
  ]
}
```

Dimensional inputs also use canonical fields:

```json
{
  "riderWeightKg": 81.6466266
}
```

The UI may have accepted `180 lb`; the client converts it before creating the
request.

## 5. Shared Server Model

The server keeps measurement-system semantics in shared types. Controllers and
repositories must not define milestone thresholds or conversions independently.

The shared model contains:

- `MeasurementSystem` with `METRIC` and `US_CUSTOMARY`
- `MeasurementConversions` with the exact named constants
- `MilestoneDimension` and `MilestoneDefinition`
- `MilestoneProfile` containing the definitions for one system
- `MeasurementSystemService` resolving profiles and canonical thresholds

Conceptually:

```java
public record MilestoneDefinition(
    MilestoneDimension dimension,
    double thresholdCanonical,
    CanonicalUnit canonicalUnit,
    int sortOrder
) {}

public interface MeasurementSystemService {
    MilestoneProfile milestones(MeasurementSystem system);
}
```

Repositories receive canonical thresholds. They do not know about miles,
feet, pounds, browser preferences, or display labels.

## 6. API Properties To Change

Canonical units must be visible in Java DTOs, OpenAPI, and generated TypeScript
types. The following existing properties are ambiguous or use inconsistent
suffixes.

### Track and point data

| Current property | Target property |
|---|---|
| `trackLengthInMeter` | `trackLengthM` |
| `ascentInMeter` | `ascentM` |
| `descentInMeter` | `descentM` |
| `minAltitude` | `minAltitudeM` |
| `maxAltitude` | `maxAltitudeM` |
| `avgDistanceBetweenPoints` | `avgDistanceBetweenPointsM` |
| `medianDistanceBetweenPoints` | `medianDistanceBetweenPointsM` |
| `maxDistanceBetweenPoints` | `maxDistanceBetweenPointsM` |
| `speedInKmh30sMax` | `speed30sMaxKmh` |
| `elevationGainPerHour30sMax` | `elevationGain30sMaxMPerH` |
| `elevationLossPerHour30sMax` | `elevationLoss30sMaxMPerH` |
| `pointAltitude` | `pointAltitudeM` |
| `distanceInMeterBetweenPoints` | `distanceBetweenPointsM` |
| `distanceInMeterSinceStart` | `distanceSinceStartM` |
| `ascentInMeterBetweenPoints` | `ascentBetweenPointsM` |
| `ascentInMeterSinceStart` | `ascentSinceStartM` |
| `descentInMeterSinceStart` | `descentSinceStartM` |
| `speedInKmhMovingWindow` | `speedMovingWindowKmh` |
| `elevationGainPerHourMovingWindow` | `elevationGainMovingWindowMPerH` |
| `elevationLossPerHourMovingWindow` | `elevationLossMovingWindowMPerH` |
| `GpsTrackData.precisionInMeter` | `precisionM` |
| `GpsTrackEvent.startDistanceInMeter` | `startDistanceM` |
| `GpsTrackEvent.endDistanceInMeter` | `endDistanceM` |

Already explicit properties such as `energyWeightKgUsed`, `powerWattsAvg`, and
`energyNetTotalWh` remain unchanged.

### Statistics, crossings, and map results

| Current property | Target property |
|---|---|
| `trackLengthInMeterSum` | `distanceSumM` |
| `trackLengthInMeterMed` | `distanceMedianM` |
| `trackLengthInMeterAvg` | `distanceAverageM` |
| `distanceBetweenGpsPointsMed` | `distanceBetweenGpsPointsMedianM` |
| `CrossingPointsRequest.radius` | `radiusM` |
| `avgSpeedSinceLastTriggerPoint` | `averageSpeedSinceLastTriggerPointKmh` |
| `distanceInMeterSinceLastTriggerPoint` | `distanceSinceLastTriggerPointM` |
| `distanceToTriggerPointInMeter` | `distanceToTriggerPointM` |
| `IWayPointWithDistance.distanceToPoint` | `distanceToPointM` |
| `IWayPointWithDistance.elevation` | `elevationM` |
| `LocationSearchResultDto.distance_meters` | `distanceM` |
| request parameter `precisionInMeter` | `precisionM` |
| request parameter `distanceInMeter` | `distanceM` |

Milestones must replace stringly typed `rowKey` and `value` properties with a
typed dimension and dimension-specific canonical threshold and achieved-value
fields. The response also carries the evaluated `measurementSystem`.

Overview rankings remain canonical and profile-independent, but their generic
`value` property needs a definition on each ranking group:

| Current property | Target property |
|---|---|
| none | `TrackRanking.dimension` |
| none | `TrackRanking.canonicalUnit` |
| `TrackRef.value` | keep as the canonical value described by its ranking group |

### Dynamic metadata

Dynamic fields cannot express their dimension through a fixed DTO property.
Their definitions remain part of the canonical payload:

| Current property | Target property |
|---|---|
| `MetricDefinition.unit` | `canonicalUnit` |
| none | `MetricDefinition.dimension` |
| `FilterParamMetadata.unit` | `canonicalUnit` |
| none | `FilterParamMetadata.dimension` |
| `FilterGradientMetadata.metricUnit` | `metricCanonicalUnit` |
| none | `FilterGradientMetadata.metricDimension` |

These definitions describe canonical data. They are not a selected display
profile. Samples and filter results do not repeat them.

Chart `xStart` and `xEnd` remain canonical: seconds for `TIME` and meters for
`DISTANCE`. `xMode` must be required whenever these properties are present.

### Filter parameter names

The public filter contract uses the canonical units from section 3. Existing
DB-backed SQL names remain internal implementation details.

| Current SQL parameter | Public API parameter | Public canonical unit |
|---|---|---|
| `AVG_SPEED_MIN_KMH` | `AVG_SPEED_MIN_KMH` | `km/h` |
| `AVG_SPEED_MAX_KMH` | `AVG_SPEED_MAX_KMH` | `km/h` |
| `DISTANCE_MIN_KM` | `DISTANCE_MIN_M` | `m` |
| `DISTANCE_MAX_KM` | `DISTANCE_MAX_M` | `m` |
| `ELEVATION_GAIN_MIN_M` | `ELEVATION_GAIN_MIN_M` | `m` |
| `ELEVATION_GAIN_MAX_M` | `ELEVATION_GAIN_MAX_M` | `m` |
| `ENERGY_MIN_KWH` | `ENERGY_MIN_WH` | `Wh` |
| `ENERGY_MAX_KWH` | `ENERGY_MAX_WH` | `Wh` |

`FilterParamResolver` maps public names and canonical values to the existing
SQL parameters immediately before binding. This keeps the DB configuration
unchanged without exposing inconsistent units to API clients.

### Application properties

Add the following server properties:

| Java property | Configuration property | Purpose |
|---|---|---|
| `MtlAppProperties.defaultMeasurementSystem` | `mtl.default-measurement-system` | Optional typed first-visit suggestion |
| `BuildInfoResponse.defaultMeasurementSystem` | response property | Delivers the typed suggestion to the client |

The property accepts `METRIC` or `US_CUSTOMARY` and may be omitted:

```yaml
mtl:
  default-measurement-system: US_CUSTOMARY
```

The server default does not change the compatibility default of an omitted API
request parameter. Semantic requests without `measurementSystem` still use
`METRIC`.

## 7. Server-Owned Milestones

Milestones are profile-specific definitions:

| Profile | Distance | Ascent | Energy |
|---|---|---|---|
| Metric | `10`, `25`, `50`, `100 km` | `500`, `1000`, `2000 m` | `1000 Wh` |
| US customary | `5`, `10`, `25`, `50`, `100 mi` | `1000`, `2500`, `5000 ft` | `1000 Wh` |

For each definition the server:

1. resolves the exact canonical threshold
2. queries the earliest qualifying track
3. returns the threshold and achieved value in canonical units

The `100 mi` query uses exactly `160934.4 m`. It is not obtained by relabeling
or converting the `100 km` result. Tests must allow different profile
cardinalities and prove that the first `100 km` and `100 mi` tracks can differ.

Statistics totals, rankings, period distributions, and percentile buckets do
not need a profile. Linear conversion preserves their membership and ordering.

## 8. Filters And User Input

The public filter API accepts canonical values. The existing DB-backed metadata
and unit-encoded SQL parameters do not require a database change.
`FilterUiMetadataParser` maps stored metadata to public names, `dimension`, and
`canonicalUnit`. `FilterParamResolver` converts public canonical values to the
internal units expected by SQL immediately before binding.

The client stores filter values canonically. It converts them only when reading
from or writing to a control. This applies to:

- distance and elevation limits
- speed limits
- geo-circle radii
- Segment Analyzer detection radii
- rider and equipment weight

Browser-stored filter drafts containing untyped display numbers must be
versioned or invalidated once. A unit toggle must not reinterpret a stored `10`
as a different physical value.

Percentile gradient labels are already unit-free. Their result order and bucket
membership do not change between systems.

## 9. Client Responsibilities

Create `mtl-client/src/utils/units.ts` as the only general measurement
conversion module. It contains exact conversions, typed dimensions, selected
display units, and locale-aware formatters. It must not contain milestone or
other domain thresholds.

The client:

- persists the measurement-system preference
- converts canonical values for tables, charts, tooltips, popups, replay, and
  map labels
- converts displayed user input back to canonical values
- keeps chart axes, cursor synchronization, interpolation, geometry, and
  sorting in canonical units
- rerenders on preference changes without reloading the page
- refetches only responses whose semantic definitions depend on the system
- never sums or derives results from rounded display values

Track-browser search text contains generated measurement strings and must be
rebuilt when the preference changes. Suggested persistent names must not embed
formatted measurements; for example use `Plan — Aug 5`, not `Plan — 25 km`.

Measurements created solely by browser interaction, including haversine map
measurements, remain canonical internally and use the same conversion module
for display. This does not permit client-side domain thresholds.

### Preference UI

The preference is available under **Admin → Settings → Presentation**. Rename
the existing **Appearance** section to **Presentation** and place the control
beside **Format locale**. It is a global presentation setting, not a map
setting.

The control is labeled **Measurement units** and offers **Metric** and
**Imperial (US)**. A **Use default** action removes the explicit browser
preference. The selected or resolved system is shown with a short preview, for
example `25.0 km · 500 m · 80 kg`.

Changing the selection immediately rerenders all visible measurements without
reloading the page. It also refetches semantic responses such as milestones as
described in section 11. Locale continues to control number formatting only;
changing locale must not change the measurement system, and changing the
measurement system must not change locale.

## 10. Persisted And Generated Text

Do not embed formatted measurements in server-generated presentation strings.
The current GPS-gap event description, activity-classifier details, elevation
load messages, and similar diagnostics contain metric values that cannot be
converted safely.

Normal UI content must use structured canonical fields and client-generated
text. Technical diagnostics may remain canonical if they are explicitly
classified as diagnostics and are not parsed for behavior. In particular, the
client must not identify an event by matching a description prefix.

No legacy text backfill is required.

## 11. Cache Behavior

Canonical track metadata, geometry, chart data, and planner data are independent
of the measurement preference. Do not duplicate or clear the large IndexedDB
track cache on a unit toggle.

Derived display state is rebuilt locally. Canonical filter drafts remain valid.
Only semantic responses, currently the milestone overview, include the
measurement system in their request/cache key. Late responses for an obsolete
system are discarded.

## 12. OpenAPI And Testing

OpenAPI is the API type source of truth. Implement server DTO changes first,
save the live `/mtl/v3/api-docs` schema, and regenerate the TypeScript client
with Maven. Do not add legacy aliases for renamed fields; generated TypeScript
errors are the migration mechanism.

Tests must cover:

- canonical unit and property-name contracts
- exact conversion constants in the shared client module
- metric default and invalid semantic profile rejection
- milestone boundaries around exact canonical thresholds
- different metric and US milestone results and cardinalities
- canonical filter storage and input conversion
- preference persistence and reactive formatting
- automatic preference resolution and **Use default** behavior
- the global preference under **Admin → Settings → Presentation**
- canonical cache reuse across preference changes
- stale semantic-response rejection
- dynamic metric and filter dimension metadata
- absence of hardcoded display units outside the unit module

## 13. Non-Goals

This architecture does not add:

- converted API response profiles
- response-level `meta.units` blocks
- units repeated beside every fixed numeric value
- database migrations or converted-value backfills
- duplicated metric and US customary cache entries
- client-side milestone calculations
- conversion of technical precision, geometry, synchronization, routing,
  activity classification, stop detection, or energy-physics thresholds
- conversion of GPX export, `/gpx`, source-file download, or SQL preview data
