# Packet: FIT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FIT_01.
- In scope: import the verified FIT activity with GPS positions.
- Out of scope: indexing, map, search, and statistics results, covered by FIT_02.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05 and DEL_05.
- Required app/data state: synchronized three-GPX state; verified public FIT staged outside the watched folder.
- Required browser context: signed-in map left open for later freshness observation.

## Allowed Mutations

- Allowed: copy exactly the staged public Activity.fit into the disposable watched folder.
- Not allowed: alter the FIT, overwrite another source, or add non-GPS FIT evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Rechecked the official Activity.fit checksum, copied it into the documented watched folder, and verified destination size/count. | One GPS-bearing FIT source enters the import pipeline unchanged. | Activity.fit (94,096 bytes; expected SHA-256) was copied in 7 ms; watched-folder count changed 3→4. | PASS | [assets/FIT_01-import.txt](../assets/FIT_01-import.txt); [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import.txt](../assets/FIT_01-import.txt) | FIT checksum, path, copy timing, and watched-folder count. |
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Official-source integrity and GPS/timestamp record validation. |

## Screenshot Evidence

Not needed; this packet covers the watched-folder import mutation. UI evidence follows in FIT_02.

## Timings

| Step | Timing |
|---|---:|
| FIT copy | 7 ms |

## Handoff Notes

- Completed: verified GPS-bearing FIT placed in the watched import folder.
- Remaining unfinished coverage: FIT_02 onward; DAT_03 awaits the FIT imported mapping from FIT_02.
- Blocked or not applicable: none.
- State left for the next packet: four watched files; browser still shows the synchronized pre-FIT three-track state until processing/freshness.
