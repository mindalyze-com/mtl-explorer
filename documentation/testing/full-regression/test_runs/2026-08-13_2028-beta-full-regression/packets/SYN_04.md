# Packet: SYN_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SYN_04.
- In scope: aggregate FIT import freshness/cache behavior.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01 and FIT_02.
- Required app/data state: executed three-track baseline to FIT import transition.
- Required browser context: map, Track Browser, Statistics, and details evidence from FIT_02.

## Allowed Mutations

- Allowed: none; consolidate the direct FIT execution evidence.
- Not allowed: re-import the same FIT.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_04 | Reconciled the executed Activity.fit copy, conversion, freshness Reload, map, Statistics, search, and details evidence. | FIT conversion changes freshness and cache state like a native GPX import. | The unchanged official FIT converted successfully, produced the freshness flow, and changed all requested cached surfaces from three to four tracks with a searchable populated record. | PASS | [sequence](../assets/SYN_04-fit-sync.txt), [FIT processing](../assets/FIT_02-processing.txt), [FIT packet](FIT_02.md) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_04-fit-sync.txt](../assets/SYN_04-fit-sync.txt) | FIT cache/freshness result. |
| [assets/FIT_02-processing.txt](../assets/FIT_02-processing.txt) | Conversion and job timing. |
| [packets/FIT_02.md](FIT_02.md) | Map, Statistics, search, and details evidence. |

## Screenshot Evidence

See FIT_02 packet evidence; this aggregate packet adds no duplicate screenshot.

## Timings

| Step | Timing |
|---|---:|
| FIT ingest success | 12.679 s |
| Background enrichment settlement | 11.342 s after ingest |

## Handoff Notes

- Completed: SYN_04 is terminal `PASS`.
- Remaining unfinished coverage: SYN_05 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Q1 Statistics at 8/12 current baseline.

