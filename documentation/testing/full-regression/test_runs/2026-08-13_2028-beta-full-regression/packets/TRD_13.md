# Packet: TRD_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_13.
- In scope: previous/next, duplicates, and related-card navigation.
- Out of scope: segment siblings.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_12.
- Required app/data state: #100004 has three detected duplicates; timeline neighbors exist.
- Required browser context: Track Details Related tab.

## Allowed Mutations

- Allowed: navigate among read-only related cards.
- Not allowed: change duplicate classification.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_13 | Opened #100016 Related, navigated through a previous card to #100004, inspected all related groups, then clicked a duplicate. | Duplicates and previous/next appear; clicking navigates. | #100016 showed 7 previous/4 next. #100004 showed 2 previous, 9 next, and 3 duplicates. Card clicks navigated to #100004 and then duplicate #100007 with matching URLs/IDs. | PASS | [related navigation](../assets/TRD_13-related-navigation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) | Group counts, duplicate identities, and both navigation results. |

## Screenshot Evidence

Exact related group contents and routed track IDs are recorded as text; no screenshot is needed.

## Timings

| Step | Timing |
|---|---:|
| Each related-card navigation | < 1 s |
| Group audit | < 1 min |

## Handoff Notes

- Completed: TRD_13.
- Remaining unfinished coverage: TRD_14 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: duplicate #100007 Track Details open.

