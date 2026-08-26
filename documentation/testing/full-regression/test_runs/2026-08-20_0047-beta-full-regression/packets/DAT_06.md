# Packet: DAT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_06
- In scope: Do not count non-GPS FIT or waypoint-only GPX as positive evidence; exercise a clear negative outcome.
- Out of scope: Mutating the empty baseline before IMP_01.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05 and ADM_02.
- Required app/data state: Clean empty baseline until IMP_01; normal eight-track state during ADM_02.
- Required browser context: Admin upload plus authenticated production endpoint because the connected browser cannot attach a local file.

## Allowed Mutations

- Allowed: Stage and validate a fully synthetic waypoint-only negative fixture outside watched folders; update this packet after negative upload execution.
- Not allowed: Count the negative fixture as positive evidence or import it before baseline capture.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_06 | Confirm positive inputs have real GPS tracks; upload a synthetic GPX containing one waypoint and zero trackpoints; follow its index/UI outcome and clean it up. | Non-GPS/waypoint-only data is excluded from positives and produces a clear fail/ignore result without corrupting UI/stats. | Fixed locally: the same waypoint-only fixture is rejected in Admin before the watched directory with a clear no-track-points result on desktop/mobile. No cleanup or indexer recovery is needed. | FIXED | [original](../assets/DAT_06-negative-fixture.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-020-fix-local-desktop.webp); [mobile](../assets/MTL-FR-020-fix-local-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Status | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-020 | P2 | Waypoint-only track upload reports success, then silently indexes as empty. | Upload a GPX containing a waypoint but no trackpoints. | A clear fail/ignore result is shown. | Fixed locally: upload returns a clear HTTP 400 result and creates no watched file. | [original](../assets/ADM_02-upload.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt) | FIXED | Invalid/non-track data no longer enters indexing. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_06-negative-fixture.txt](../assets/DAT_06-negative-fixture.txt) | Positive qualification and staged negative fixture metadata. |

## Screenshot Evidence

![Desktop waypoint-only negative fixture](../assets/MTL-FR-020-fix-local-desktop.webp)

![Mobile waypoint-only negative fixture](../assets/MTL-FR-020-fix-local-mobile.webp)

## Fix Record

- Native GPX validation now rejects the negative fixture before disk write.
- See [local evidence](../assets/MTL-FR-005-021-fix-local.txt) and ADM_02 for the end-user flow.

## Timings

| Step | Timing |
|---|---:|
| Negative fixture creation/validation | <1 s |
| Product negative ingest | 31 ms after watcher pickup |

## Handoff Notes

- Completed: Positive qualification, negative upload/index result, unaffected 8-track UI, and exact cleanup.
- Remaining unfinished coverage: None for DAT_06.
- Blocked or not applicable: Durable screenshots and chooser attachment are tool-blocked; the product failure is independently established.
- State left for the next packet: Negative and positive temporary uploads removed; GPX-UPLOAD empty.
