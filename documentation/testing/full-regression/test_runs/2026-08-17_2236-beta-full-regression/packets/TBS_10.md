# Packet: TBS_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_10
- In scope: Statistics entry activation for period drill-down, track navigation, and map highlighting.
- Out of scope: Full highlight ranking lists and exclusions covered by TBS_11.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_09.
- Required app/data state: Stable 13-track post-delete set.
- Required browser context: Statistics Overview and Tracks on desktop.

## Allowed Mutations

- Allowed: Open/close drill-down, open/close details, search, and center a track on the map.
- Not allowed: Exclude tracks or change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_10 | Activated Most active month, a Recent Activity track, and a track shape preview. | Statistics entry navigates, filters/drills down, or highlights as appropriate. | Period entry opened a ranked four-period drill-down; Recent Activity opened matching #100017 details; shape preview kept /stats and selected/centered the matching Lannion row/map. | PASS | [assets/TBS_10-entry-navigation.txt](../assets/TBS_10-entry-navigation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_10-entry-navigation.txt](../assets/TBS_10-entry-navigation.txt) | Period drill-down, detail route/identity, and map active-row evidence. |

## Screenshot Evidence

Unavailable under ACC_04. Exact active states, drill-down rows, route/ID/detail values, map scale, and selected-row DOM state provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Period drill-down | About 2 s |
| Recent-entry detail navigation and return | About 5 s |
| Track map highlight | About 7 s |

## Handoff Notes

- Completed: Drill-down, detail navigation, and map highlight entry behavior.
- Remaining unfinished coverage: None for TBS_10.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Tracks All is open with clear search; Lannion is the current map-selected track; 13 tracks remain.
