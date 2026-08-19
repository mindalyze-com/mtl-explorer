# Packet: LOC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_04
- In scope: Zero, very large, negative-gain/slope, and null-elevation boundary formatting.
- Out of scope: Creating or directly editing production rows to force null elevation.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_03.
- Required app/data state: Current indexed regression dataset.
- Required browser context: Desktop with persisted de-DE active.

## Allowed Mutations

- Allowed: Open Statistics Tracks and Track Details; perform read-only authenticated API/database scans.
- Not allowed: Import a new boundary fixture or alter stored track values for this check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_04 | Checked large/zero rows, opened track 100005 with negative slope, scanned UI for invalid tokens, and audited raw tracks/data-point altitude for a null fixture. | All boundary values, including null elevation, render sensibly. | Large, zero, and negative values rendered correctly with no `NaN`, `undefined`, `null`, or `Infinity`. Current data contained zero null altitude points and no top-level null-elevation case, so that required branch could not be exercised safely. | BLOCKED | [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt); [assets/LOC_04-negative-boundary.jpg](../assets/LOC_04-negative-boundary.jpg) |

## Issues

- None. The blocker is missing test data, not observed product failure.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) | Exact large/zero/negative values, raw-data facts, and invalid-token scan. |
| [assets/LOC_04-negative-boundary.jpg](../assets/LOC_04-negative-boundary.jpg) | Track Details with explicit zero fields and `-100,7%` descent slope. |

## Screenshot Evidence

- The detail screenshot shows finite positive metrics, explicit zero fields, and a finite negative descent slope with no formatting leakage.

## Timings

| Step | Timing |
|---|---:|
| UI and raw-data boundary sweep | About 8 seconds |

## Handoff Notes

- Completed: Large, zero, and negative boundary formatting passed; bad-token scan passed.
- Remaining unfinished coverage: None; LOC_04 is terminal.
- Blocked or not applicable: Null elevation could not be exercised because the frozen dataset had zero null altitude points; no unsafe database mutation was made.
- State left for the next packet: de-DE active in Preferences; data unchanged.
