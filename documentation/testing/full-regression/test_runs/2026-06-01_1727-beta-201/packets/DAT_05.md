# Packet: DAT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_05
- In scope: Use at least one public FIT activity file with GPS positions.
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
| DAT_05 | Downloaded and audited the public test data relevant to this row. | Public data selection satisfies this coverage row and records required metadata. | Downloaded and staged Garmin FIT SDK `Activity.fit` from the suggested source; checksum 949a238e1bb75c3684479785f76fa9a16888bb394518844248f488171d591387 and byte size 94096 recorded. GPS display/conversion will be verified in FIT packets after import. | PASS | [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) |

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

- Completed: DAT_05 terminal as `PASS`.
- Remaining unfinished coverage: Continue with next queue ID.
- Blocked or not applicable: None.
- State left for the next packet: Public samples are staged on the target under `/root/mtl-regression-2026-06-01_1727-beta-201/test-inputs/`; watched import folder is still empty.
