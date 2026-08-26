# Packet: DAT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_03
- In scope: Complete per-file source, license note, destination name, checksum, size, trackpoint/timestamp count, imported IDs, and imported names.
- Out of scope: Import before the IMP baseline packet can capture an empty state.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_02 and IMP_02-IMP_06.
- Required app/data state: Five staged files imported and indexed.
- Required browser context: Track browser/Admin after import.

## Allowed Mutations

- Allowed: Record source metadata and update this same packet after indexing provides imported identities.
- Not allowed: Mark PASS without imported IDs/names or import before the baseline is captured.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_03 | Assemble per-file provenance and validation manifest, then add identities after the required import flow. | Every source file has source URL/page/license note, destination name, SHA-256, bytes, trkpt, timestamps, imported IDs, and imported names. | All fields are complete for all five files. Imported IDs 100000-100004 and names were confirmed in the live watcher and direct UI search/detail checks. | PASS | [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt); [assets/DAT_01-public-gpx.txt](../assets/DAT_01-public-gpx.txt); [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Per-file source and pending imported-identity manifest. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; imported identities are recorded in linked text evidence.

## Timings

| Step | Timing |
|---|---:|
| Source manifest assembly and identity update | 2 min |

## Handoff Notes

- Completed: Source URL/page/license note, destination name, SHA-256, size, trkpt count, timestamp count, imported ID, and imported name for every file.
- Remaining unfinished coverage: None for DAT_03.
- Blocked or not applicable: None.
- State left for the next packet: Five public GPX identities are available for later checks.
