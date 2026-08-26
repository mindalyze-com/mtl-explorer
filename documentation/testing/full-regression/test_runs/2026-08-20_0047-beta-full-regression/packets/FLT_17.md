# Packet: FLT_17

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_17
- In scope: Automatic first-visit guidance and durable Got it dismissal.
- Out of scope: Returning-user manual Read more flow and narrow layout, covered by FLT_18.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_16.
- Required app/data state: Disposable app loaded with nine tracks.
- Required browser context: Unique clean origin with isolated local storage.

## Allowed Mutations

- Allowed: Sign in, open Filter, dismiss guidance, close and reopen Filter.
- Not allowed: Alter dataset or application settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_17 | Open Filter in a clean context, inspect guidance, click Got it, then reopen. | Guidance opens automatically with Important; Got it prevents repetition. | Required sections and labels appeared automatically; reopened Filter showed the overview without Important or Got it. | PASS | [assets/FLT_17-first-visit-guidance.txt](../assets/FLT_17-first-visit-guidance.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_17-first-visit-guidance.txt](../assets/FLT_17-first-visit-guidance.txt) | Clean-context setup, automatic guidance content, and repeat-open state. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible guidance structure and labels are linked above.

## Timings

| Step | Timing |
|---|---:|
| Isolated sign-in, first open, dismiss, and reopen | 5 min |

## Handoff Notes

- Completed: First-visit guidance and persistent dismissal.
- Remaining unfinished coverage: None for FLT_17.
- Blocked or not applicable: None.
- State left for the next packet: Isolated returning-user Filter overview open after Got it.
