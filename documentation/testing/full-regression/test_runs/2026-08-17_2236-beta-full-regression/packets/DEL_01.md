# Packet: DEL_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DEL_01
- In scope: Remove exactly two original public GPX imports from the watched folder.
- Out of scope: Delete processing and UI absence checks covered by DEL_02-DEL_04.

## Prerequisites

- Required previous coverage IDs or run packets: All checks requiring the complete five-GPX set.
- Required app/data state: Five mapped public sources and preserved copies outside the watched tree.
- Required browser context: Not required for this filesystem mutation.

## Allowed Mutations

- Allowed: Recoverably move exactly two recorded public GPX sources to run-specific quarantine.
- Not allowed: Remove unrelated sources or the three retained imports.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Moved Vitry-le-Francois_Langres.gpx and VoieVerteHauteVosges.gpx from the run's watched five-GPX folder into its recoverable deletion quarantine, then listed both locations. | Exactly two imported sources leave the watched folder; three remain. | The watched folder contains only JuraRoute72011.gpx, MoselradwegAusWiki.gpx, and Lannion_Plestin_parcours24.4RE.gpx. Both targets are present in quarantine with their original byte sizes; no unrelated source moved. | PASS | [assets/DEL_01-source-removal.txt](../assets/DEL_01-source-removal.txt); [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-source-removal.txt](../assets/DEL_01-source-removal.txt) | Exact before/after watched set, deletion targets, byte sizes, track IDs, and quarantine path. |
| [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) | Original five-file watched set. |

## Screenshot Evidence

Not applicable to the watched-folder mutation; user-visible results are captured in DEL_03 and DEL_04.

## Timings

| Step | Timing |
|---|---:|
| Recoverable two-file move | Completed before final delete verification |

## Handoff Notes

- Completed: Exactly two original public GPX sources removed from the watched tree.
- Remaining unfinished coverage: None for DEL_01.
- Blocked or not applicable: None.
- State left for the next packet: Three retained sources watched; two deletion targets quarantined.
