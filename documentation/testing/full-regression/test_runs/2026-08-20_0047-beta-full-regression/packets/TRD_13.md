# Packet: TRD_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_13
- In scope: Duplicate, previous, and next related tracks plus navigation.
- Out of scope: Event-related navigation.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_01 and TRD_03.
- Required app/data state: Canonical track 100009 with five duplicate-format tracks.
- Required browser context: Authenticated Related tab.

## Allowed Mutations

- Allowed: Navigate through related cards.
- Not allowed: Change track metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_13 | Inspect canonical track 100009 Related, then select duplicate, next, and previous cards. | Duplicate and timeline groups appear; selecting a card navigates to it. | Related showed 7 previous, 1 next, and 5 duplicate tracks. Cards navigated to 100006, 100010, and 100013 with matching URLs, TrackIDs, and fresh detail loads. | PASS | [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) | Group counts and three navigation targets. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible group counts and route transitions are linked above.

## Timings

| Step | Timing |
|---|---:|
| Related groups and three navigations | 5 min |

## Handoff Notes

- Completed: Duplicate, previous, and next groups plus navigation.
- Remaining unfinished coverage: None for TRD_13.
- Blocked or not applicable: None.
- State left for the next packet: Track 100013 details open; shared data unchanged.
