# Energy and power estimates

## Mechanical work and power

`EnergyService` calculates external mechanical work from supported canonical
intervals after activity classification. Models account for applicable climbing,
drag, rolling/friction, and acceleration. Mechanical Wh and metabolic kcal are
separate results; measured device-power series are not imported by this model.

Moving average power divides work during stored moving intervals by their elapsed
time. Normalized Power uses a trailing 30-second window and fourth-power weighting;
Max 30 s Power is the highest trailing average. Segment summaries retain their
separate full-density maximum-power contract.

Statistics aggregates mechanical work and derived power metrics, not metabolic
calories. Intensity Index is Normalized Power divided by the configured threshold;
Training Load is its square times moving hours times 100. The configuration key
is `fitness/user/thresholdPowerWatts` in `config`, with a 150 W fallback. This is
an installation setting, not a measured personal threshold or a per-user account.

See [EnergyService](../../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/energy/EnergyService.java)
for current calculations and configuration.

## Persistence and timing

The durable energy worker locks each track and calculates from canonical points
in one transaction. Points are loaded as read-only Hibernate entities; the existing
point repository uses a native bulk query to write only the eight energy/power fields.
The track repository writes only the mechanical, metabolic, and power summary fields.
The loaded track remains an unchanged calculation input; worker diagnostics go to the
server log. A failed chunk rolls back
the track's complete calculation. The queue acknowledges only the generation it
read, after the calculation transaction commits.

Canonical-point changes queue photo correlation only when interpolation inputs
change: parent, position, timestamp, ordering, altitude, interval qualification,
cumulative distance, or elapsed time. Energy-only updates do not queue photos.

The service logs load, calculation, and point-write phases before commit. The
worker's `Track energy transaction committed` timer includes transaction flush
and commit; batch timing also includes queue acknowledgments.

## Metabolic calories

Track Overview shows estimated **active calories** and **total calories** alongside
mechanical work. The weight dialog previews and saves both calculations. Calories
describe the person; vehicle work is never converted into human calories.

Active calories exclude standard resting expenditure. Total calories add rest for
the same covered intervals. The conversion uses 1 MET = 3.5 ml O₂/(kg min) and
5 kcal/l O₂ (1.05 kcal/(kg h)). Recorded stops contribute rest only. Body weight,
method, model version, coverage and totals are stored in `gps_track`.

## Activity models

| Activity | Model for moving intervals |
|---|---|
| Walking, hiking | Minetti walking cost by grade, body weight and surface distance |
| Running | Minetti running cost by grade, body weight and surface distance |
| Bicycle | ACSM leg cycling equation applied to estimated mechanical power |
| Mountain biking | Compendium general mountain biking: 8.5 MET |
| Rowing | Compendium recreational rowing: 3.5 MET |
| Kayaking | Compendium moderate kayaking: 5 MET |
| Stand-up paddle | Compendium general stand-up paddling: 6.5 MET |
| Skiing | Compendium general skiing: 7 MET |
| Car | Compendium passenger: 1.3 MET; driver/passenger role is unknown |
| Motorbiking | Compendium motorcycling: 2.8 MET |
| Airplane, supersonic | Seated passenger proxy: 1.3 MET; no aircraft-energy contribution |

## Models and limits

[Minetti et al. (2002)](https://pubmed.ncbi.nlm.nih.gov/12183501/) provides net
locomotion costs in J/(kg m). The published grade polynomials are evaluated only
within −45% to +45%. Smoothed grade is preferred, with denoised elevation differences
as fallback only over at least one metre of horizontal distance. A valid smoothed
grade does not require endpoint elevations. Horizontal GPS distance is converted to distance along the slope.
Downhill cost stays positive and increases again on steep descents. Walking assumes
an economical pace on firm ground; it is not a backpack-load or technical-terrain
model. Equipment mass is not substituted for body mass. No extra climbing term is
added because the cost curve already includes grade.

The ACSM cycling equation is `VO₂ = 7 + 10.8 × watts / bodyWeightKg`, in ml/(kg min).
The resting term is subtracted once for active calories. See the equation used by
[Swain et al. (2004)](https://pubmed.ncbi.nlm.nih.gov/15292752/).
This is an outdoor approximation of a steady-state ergometer model, most applicable
at moderate power. The unloaded-pedaling term supplies a light movement allowance
while coasting; GPS cannot establish cadence or distinguish all pedaling from braking.
The input is estimated external work, without a measured drivetrain correction.
Wind, assistance and individual efficiency are unknown. Intervals whose mechanical
energy was limited as implausible are omitted. Cycling also requires a finite accepted
elevation change for each moving interval; absent elevation cannot be treated as flat.

Other activities use the [2024 Compendium](https://pacompendium.com/adult-compendium/):
[cycling](https://pacompendium.com/bicycling/),
[water activities](https://pacompendium.com/water-activities/),
[winter activities](https://pacompendium.com/winter-activities/) and
[transportation](https://pacompendium.com/transportation/).
These are coarse population averages, not individualized measurements. Skiing does
not identify lift rides; water activities do not identify drift; MTB does not resolve
technical difficulty. The GUI states the assumption for each model. No heart-rate
calibration or device calorie totals are mixed into these estimates.

## Coverage and recalculation

Only supported, timed intervals with known moving/stopped state contribute.
Missing grade and elevation for graded locomotion, invalid time, unknown motion,
recording gaps, sparse sampling and unsupported gradients are omitted. Exact repeated
observations with the same timestamp and no horizontal or vertical movement are
harmless; untimed movement remains excluded. A partial estimate is the sum of covered
intervals, never extrapolated to the full track. If none are covered, calories are
unavailable, not zero. Missing timestamps can leave unquantifiable excluded time.

New imports and activity changes use the existing energy queue and canonical points.
Google Health exercise association queues energy for the first classification or
a changed activity type. Repeated metadata, source attribution, and aliases that
map to the same activity preserve calculated results and pending retry requests.
The comparison locks the current track in the association transaction.
Activity edits clear the previous totals and commit a durable queue request with the
new activity. Failures remain queued for retry. Metadata saves apply editable fields
to the locked current track, preserving every background result instead of merging
a client copy. Source replacements preserve saved body weight without copying old
calories. Weight changes save mechanical and metabolic totals together. The detail
view reloads energy charts after completed changes, with HTTP cache revalidation. Changeset **082**
requests a one-time full GPS reimport for upgrades from the previous release.
Changeset **089** adds the metabolic columns and requests the existing energy worker
once for ready imported tracks, including development databases where 082 already
ran. It does not reset source files or delete tracks. Subsequent startups do not
repeat either completed migration. See [release upgrade instructions](../../release-notes/v.0.9.0.md).

Changeset **090** adds nullable coverage-explanation fields without a backfill.
Saved body weight takes precedence over the configured default for recalculation
and previews. Configured weights must be finite and within 35–180 kg; invalid values
use 75 kg.

Water-sport external work includes drag and acceleration, with altitude changes
excluded. River descent is not credited against paddling work, and elevation noise
cannot add ascent work. Water-sport calorie models remain the activity MET estimates
listed above. Moving intervals with unavailable or limited mechanical work are
excluded. Intervals whose estimated mechanical work exceeds gross metabolic energy
are also excluded with an explicit reason. This is a physical consistency check,
not a calibrated intensity or water-current model. Known zero speeds are preserved before any average-speed fallback.

Model version `2026-09-30-v3` identifies this implementation. These are track totals;
metabolic time-series charts and metabolic segment/statistics aggregation are not
part of this version.

See [the user guide](../../user/analysis/energy.md) for the controls.
