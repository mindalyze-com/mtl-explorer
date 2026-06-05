# Packet: DAT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_02
- In scope: Prefer timestamped trackpoints.
- Out of scope: Import/index UI verification; those are covered by IMP/FIT packets.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, ACC_01-ACC_05.
- Required app/data state: Quick-install target running; public sample staging folder available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Download public sample files into `/root/mtl-regression-2026-06-01_1727-beta-201/test-inputs/` and write manifest evidence.
- Not allowed: Import files into watched folder yet; use private/local GPX tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_02 | Downloaded and audited the public test data relevant to this row. | Public data selection satisfies this coverage row and records required metadata. | All five selected GPX files are timestamped; timestamp counts equal trkpt counts for every file: JuraRoute72011.gpx 1414 trkpt/1414 time; MoselradwegAusWiki.gpx 2954 trkpt/2954 time; Vitry-le-Francois_Langres.gpx 1688 trkpt/1688 time; VoieVerteHauteVosges.gpx 1298 trkpt/1298 time; Lannion_Plestin_parcours24.4RE.gpx 381 trkpt/381 time. | PASS | [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) | Public sample source URLs, license/source notes, checksums, sizes, GPX trkpt/timestamp counts, and source track names. |

## Timings

| Step | Timing |
|---|---:|
| Public sample download and manifest generation | ~1 second |

## Handoff Notes

- Completed: DAT_02 terminal as `PASS`.
- Remaining unfinished coverage: Continue with next queue ID.
- Blocked or not applicable: None.
- State left for the next packet: Public samples are staged on the target under `/root/mtl-regression-2026-06-01_1727-beta-201/test-inputs/`; watched import folder is still empty.
