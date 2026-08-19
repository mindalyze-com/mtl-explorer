# Packet: TRD_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_13
- In scope: Related duplicate and previous/next lists plus card navigation.
- Out of scope: Chronological sorting beyond the rendered relationship groups.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_01 and TRD_02.
- Required app/data state: Duplicate-excluded track 100006 and chronological neighboring tracks.
- Required browser context: Track Details Related tab.

## Allowed Mutations

- Allowed: Activate read-only relationship cards.
- Not allowed: Modify duplicate classification.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_13 | Repeated Previous, Next, and Duplicate card activation on the matching beta build at desktop and mobile sizes. | Activating a related card navigates to that track. | Each tested beta card changed the route and detail identity to the selected track. The earlier inert-card result did not reproduce. | REJECTED | [retest](../assets/TRD_13-retest.txt); [desktop](../assets/TRD_13-rejected-desktop.webp); [mobile](../assets/TRD_13-rejected-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-003 | P1 | Related-track cards do not navigate. | Open `/mtl/track/100006`, select Related, and activate a visible previous, next, or duplicate card. | The selected related track opens. | The route and detail identity remain on track 100006 after all three card types are activated. | [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) | Users cannot follow duplicate or chronological relationships from Track Details. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_13-related-navigation.txt](../assets/TRD_13-related-navigation.txt) | Rendered counts/card examples and before/after route/identity values for three relationship types. |

## Screenshot Evidence

Unavailable under ACC_04. Rendered relationship labels plus exact before/after route and identity values provide direct UI evidence.

## Timings

| Step | Timing |
|---|---:|
| Relationship inspection | About 2 s |
| Three navigation attempts | About 3 s |

## Handoff Notes

- Completed: Duplicate, previous, and next rendering plus activation behavior.
- Remaining unfinished coverage: None for TRD_13; failure is terminal and tracked as FR-003.
- Blocked or not applicable: None.
- State left for the next packet: Track 100006 Related open and unchanged.

## Remediation Verification

- Finding FR-003 is `REJECTED`: route ownership and related-card navigation worked in the matching beta build.
- No product change was made. Evidence is linked in the action row.
