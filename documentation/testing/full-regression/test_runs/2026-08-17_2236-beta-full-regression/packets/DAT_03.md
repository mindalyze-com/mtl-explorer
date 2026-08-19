# Packet: DAT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_03
- In scope: Per-source URL, source/license note, filename, checksum, byte size, trackpoint/timestamp counts, imported track IDs, and imported names.
- Out of scope: Import before the IMP_01 baseline.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_02; IMP_01-IMP_04 for imported IDs/names.
- Required app/data state: Five staged GPX files; empty watched import folder until baseline.
- Required browser context: Signed-in browser after import for user-facing name checks.

## Allowed Mutations

- Allowed: Build the pre-import source manifest now and complete it after import.
- Not allowed: Invent imported IDs/names before indexing.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_03 | Recorded all source metadata and source-page/license note, then completed the manifest with IDs and names obtained by opening every imported track from user-facing search after IMP_01-IMP_06. | Every source record includes URL, license note, filename, SHA-256, bytes, trackpoints, timestamps, imported IDs, and imported names. | All fields are complete. The five sources map one-to-one to track IDs 100000-100004 and the exact imported names recorded in the manifest. | PASS | [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt); [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | Per-file source metadata with explicit pending imported fields. |
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | Exact raw URLs and structural preflight. |

## Screenshot Evidence

Browser screenshots are blocked by ACC_04; direct user-navigation ID/name evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Pre-import manifest assembly | <1 s |

## Handoff Notes

- Completed: URLs, source/license note, filenames, sizes, hashes, trackpoint counts, and timestamp counts.
- Remaining unfinished coverage: None for DAT_03.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Complete five-source manifest is durable; imported sources remain present for later checks.
