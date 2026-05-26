# Packet: TBS_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_02
- In scope: track browser search across visible metadata fields.
- Out of scope: sorting and row navigation.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01.
- Required app/data state: Stats Tracks tab open with 10 tracks.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: type and clear search terms.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_02 | Searched the Tracks tab for `Jura`, `IGCHDRS`, `20/07/2021`, `273 km`, `7h 46m`, `Walking`, and `Activity.fit`. | Search matches names, descriptions, dates, distances, durations, activity, and file paths. | Each representative term returned matching visible rows, including FIT source-file/path search returning Track #100005. Search was cleared afterward. | PASS | `assets/TBS_02-search-results.txt`, `assets/TBS_02-file-search.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_02-search-results.txt | Search term matrix and representative matching rows. |
| assets/TBS_02-file-search.webp | Track browser after searching for `Activity.fit`. |

## Timings

| Step | Timing |
|---|---:|
| Track browser search matrix | <3m |

## Handoff Notes

- Completed: TBS_02 terminal PASS.
- Remaining unfinished coverage: continue with TBS_03.
- Blocked or not applicable: none.
- State left for the next packet: Tracks tab open, search cleared.
