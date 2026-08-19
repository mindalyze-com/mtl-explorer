# Packet: LOC_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_02
- In scope: Live locale switching across Preferences and Statistics.
- Out of scope: Reload persistence, covered by LOC_03.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_01.
- Required app/data state: `en-GB` baseline recorded.
- Required browser context: Authenticated desktop session.

## Allowed Mutations

- Allowed: Select `de-DE` through Format locale.
- Not allowed: Reload during the live-update assertion.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_02 | Selected `de-DE`, read its Preferences preview, then opened the same Statistics view without reloading. | Formatting updates across the app without reload artifacts. | Preview changed to `12.345,67`; Statistics immediately changed to `3.993 Wh`, `13.090 m`, `72,5 km/h`, `18.08.2026`, and decimal commas with no blank/error state. | PASS | [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) | Paired en-GB/de-DE values and no-reload observation. |

## Screenshot Evidence

- [assets/LOC_03-de-de-persisted.jpg](../assets/LOC_03-de-de-persisted.jpg) shows the resulting de-DE preference state; this packet's text evidence distinguishes the immediate no-reload transition.

## Timings

| Step | Timing |
|---|---:|
| Preference repaint | Under 500 ms |
| Statistics repaint/navigation | Under 500 ms |

## Handoff Notes

- Completed: Live `en-GB` to `de-DE` update across Preferences and Statistics.
- Remaining unfinished coverage: None for LOC_02.
- Blocked or not applicable: None.
- State left for the next packet: `de-DE` selected, ready for reload.
