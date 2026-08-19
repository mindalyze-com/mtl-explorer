# Packet: MED_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_16
- In scope: Unsaved camera-clock preview and reset boundary.
- Out of scope: Persisting a correction.

## Prerequisites

- Required previous coverage IDs or run packets: MED_15.
- Required app/data state: Activity 100016 with four embedded-GPS and two camera-clock photos.
- Required browser context: Photos tab with Photo tools open.

## Allowed Mutations

- Allowed: Unsaved preview and reset.
- Not allowed: Saving a correction or changing source metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_16 | Corrected the synthetic fixture, reindexed it, then previewed +0.25 h on desktop and mobile. | Four genuine EXIF GPS items stay unchanged; two camera-clock items shift and remain inside the activity. | The original fixture had GPS coordinates but no GPS timestamp, so all six rows were correctly treated as camera-clock items. With valid 4/2 provenance and a 25-minute track, preview retained six, left four Photo GPS items unchanged, shifted two Estimated items, and enabled Save. | REJECTED | [retest](../assets/MED_16-retest.txt); [desktop](../assets/MED_16-rejected-desktop.webp); [mobile](../assets/MED_16-rejected-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-010 | P1 | Camera-offset preview removes embedded-GPS photos despite the stated invariant. | Open activity 100016 Photos, Photo tools, enter +0.25 h, select Preview. | Four Photo GPS items remain unchanged; only two camera-clock items are previewed. | All six items disappear and the preview reports 0 photos. | [assets/MED_16-camera-preview.txt](../assets/MED_16-camera-preview.txt) | A correction preview can make authoritative GPS photos appear missing and prevents saving. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_16-camera-preview.txt](../assets/MED_16-camera-preview.txt) | Input boundary, exact preview state, reset result, and persistence control. |

## Screenshot Evidence

A live viewport capture became available during this check and confirmed the exact browser validation and preview state; durable textual evidence records the accessible states. Earlier screenshot failures remain tracked by ACC_04 pending recheck.

## Timings

| Step | Timing |
|---|---:|
| Preview response | About 1.5 s |
| Reset response | About 1.2 s |

## Handoff Notes

- Completed: Preview/reset boundary tested; FR-010 recorded.
- Remaining unfinished coverage: None for MED_16.
- Blocked or not applicable: Save is covered separately by MED_17.
- State left for the next packet: Baseline six-photo timeline restored; no saved correction.

## Remediation Verification

- Finding FR-010 is `REJECTED`: the product behaved correctly for the original invalid fixture.
- The synthetic generator now creates four true EXIF GPS timestamps and two camera-clock rows; its track covers the minimum UI offset.
