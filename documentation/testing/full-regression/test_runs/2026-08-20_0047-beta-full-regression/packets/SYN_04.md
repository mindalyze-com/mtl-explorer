# Packet: SYN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_04
- In scope: FIT conversion import changes freshness/cache like native GPX.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01-05 and IMP GPX comparison flow.
- Required app/data state: Same-run official FIT fixture and recorded import.
- Required browser context: Same-run desktop/browser/API evidence.

## Allowed Mutations

- Allowed: No new mutation; consolidate durable same-run packets only.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_04 | Compared the recorded FIT watcher/conversion/freshness/reload sequence to native GPX import. | FIT conversion changes freshness and cache state the same way. | FIT produced one track, freshness/banner/reload synchronized the cache, and every required surface used it; original/converted downloads retained correct formats. | PASS | [assets/SYN_04-fit-freshness.txt](../assets/SYN_04-fit-freshness.txt); [packets/FIT_02.md](FIT_02.md) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_04-fit-freshness.txt](../assets/SYN_04-fit-freshness.txt) | FIT-to-GPX freshness/cache parity summary. |
| [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) | Conversion/index/UI details. |
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Original FIT integrity. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Converted GPX integrity. |

## Screenshot Evidence

Same-run live inspection is documented in FIT packets; ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Consolidation | <1 min; underlying timings are in FIT packets |

## Handoff Notes

- Completed: FIT conversion freshness/cache parity.
- Remaining unfinished coverage: None for SYN_04.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Current synchronized eight-track state unchanged.
