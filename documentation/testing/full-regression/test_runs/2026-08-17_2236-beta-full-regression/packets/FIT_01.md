# Packet: FIT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_01
- In scope: Import the public GPS-bearing FIT activity.
- Out of scope: Index/display/download outcomes.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05 and IMP_01-IMP_06.
- Required app/data state: Original FIT staged outside watcher with checksum recorded.
- Required browser context: Signed-in session may remain open.

## Allowed Mutations

- Allowed: Copy exactly `Activity.fit` into its own watched run subfolder.
- Not allowed: Replace the preserved original or import the preflight GPX conversion.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copied the preserved public GPS-bearing `Activity.fit` into a dedicated watched subfolder and verified the destination checksum. | The original FIT file enters the installed app's import watcher unchanged. | Watched-file count changed from 5 to 6 and source/destination SHA-256 values match. | PASS | [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt); [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) | Watched file count, destination, and checksum equality. |
| [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) | Public source and GPS-bearing preflight. |

## Screenshot Evidence

Not applicable to the filesystem mutation.

## Timings

| Step | Timing |
|---|---:|
| FIT watched-folder copy | 1.5 s including remote verification |

## Handoff Notes

- Completed: Original FIT imported unchanged.
- Remaining unfinished coverage: None for FIT_01.
- Blocked or not applicable: None.
- State left for the next packet: Live watcher processing FIT; browser still on the five-track map.
