# Measurement systems and API units

Persistence and domain calculations use canonical metric values. The browser
converts values for display and converts user input back to the declared unit.
Locale and measurement system are independent.

## Presentation

| System | Distance | Elevation | Speed | Vertical rate | Mass |
| --- | --- | --- | --- | --- | --- |
| Metric | km or m | m | km/h | m/h | kg |
| Imperial (US) | mi or ft | ft | mph | ft/h | lb |

Watts, watt-hours, time, percentages, and coordinates do not change. The browser
resolves an explicit preference first, then the server default, then browser
language/timezone detection. The fallback is Metric. **Use default** removes
the explicit preference; it is not another API enum value.

[useMeasurementSystem.ts](../../mtl-client/src/composables/useMeasurementSystem.ts)
owns preference resolution.
[units.ts](../../mtl-client/src/utils/units.ts) owns shared conversions and
formatting. Unit changes do not reinterpret persisted filter values.

## API contract

The [exported schema](../../mtl-api/open-api-schema/schema.json) defines actual
property names. Current names include `trackLengthInMeter`, `radius`, and
`distance_meters`; these have not all been renamed to a common suffix convention.

Most analytical requests use their declared canonical unit. An operation receives
`measurementSystem` only when the domain definition changes, such as milestone
thresholds. Statistics Overview returns the system whose milestone profile was
evaluated. Supported enum values are `METRIC` and `US_CUSTOMARY`.

SQL filter parameters retain their declared metadata units. Distance filters can
use km and energy filters kWh. The
[filter conversion helper](../../mtl-client/src/utils/filterParamUnits.ts)
converts supported controls to those declared units. Do not substitute meters
or Wh just because other API fields use them.

## Server-owned milestones

[MeasurementSystemService](../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/measurement/MeasurementSystemService.java)
owns profiles and converts their thresholds to canonical values.

| Profile | Distance milestones | Ascent milestones | Energy milestone |
| --- | --- | --- | --- |
| Metric | 10, 25, 50, 100 km | 500, 1,000, 2,000 m | 1,000 Wh |
| US customary | 5, 10, 25, 50, 100 mi | 1,000, 2,500, 5,000 ft | 1,000 Wh |

The server queries the earliest qualifying track for each definition. A 100-mile
milestone uses exactly 160,934.4 m and can select a different activity from the
100-km milestone. The browser must not derive one milestone profile from another.

The shared model uses `MeasurementSystem`, `MeasurementConversions`,
`MilestoneDimension`, `MilestoneDefinition`, and `MilestoneProfile`.
Milestone responses carry canonical meter or Wh thresholds and achieved values.
The generated client renders these fields; it does not reconstruct thresholds.

Broader API renames and dynamic unit/dimension metadata remain proposals.
They are separate from this implemented contract.
