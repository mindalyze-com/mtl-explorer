# Packet: RUN_SETUP

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md) and the GitHub `main` README quick start.
- Coverage ID or run packet: RUN_SETUP.
- In scope: target access, missing Docker prerequisites, disposable quick install, requested image override, image/build verification, documented access facts, and remote login-screen reachability.
- Out of scope: user-facing coverage IDs, data import, and cleanup.

## Prerequisites

- Required previous coverage IDs or run packets: none.
- Required app/data state: fresh disposable target and unused install path.
- Required browser context: desktop browser able to reach the remote app URL.

## Allowed Mutations

- Allowed: satisfy the enforced SSH password rotation, add resumable test SSH-key access, install missing Docker prerequisites, create the disposable install directory, create the Compose `.env`, and start the quick-install stack.
- Not allowed: inspect or modify product source, prune Docker globally, or change unrelated containers/directories.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_SETUP | Connected to `91.99.12.14`, handled its enforced password rotation, installed the missing stable Docker Engine/Compose packages, created `/root/mtl-full-regression-2026-08-13_2028-beta-full-regression`, downloaded the README Compose file, set `MTL_APP_IMAGE=wauwau0977/mytraillog:beta` in `.env` before the first start, ran `docker compose up -d`, verified Compose/container image identity and reported build, checked local/remote HTTP, and opened the remote login screen. | The README quick install starts in a disposable directory with the requested beta image; effective and running image identity, app build/version, URL, credentials source, and import folder are recorded. | Docker 29.7.2 and Compose v5.4.0 were installed on Debian 13. The app, database, BRouter, and location-search services started. Compose and the running app use `wauwau0977/mytraillog:beta`, image/digest `sha256:e3ccb3a856d377a9931ed0d395c01eacbc24c066f37c496c4d2fe4a093daa1a8`. MTL Explorer reports image `1.331` with image build `2026-08-13T18:08:12Z`. Local and remote HTTP returned 200, and the browser rendered `/mtl/login`. | PASS | [assets/RUN_SETUP-setup-summary.txt](../assets/RUN_SETUP-setup-summary.txt); [assets/RUN_SETUP-login-screen.webp](../assets/RUN_SETUP-login-screen.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_SETUP-setup-summary.txt](../assets/RUN_SETUP-setup-summary.txt) | README facts, target baseline, prerequisite setup, timings, effective/running image identity, build/version, service state, and HTTP/browser results. |
| [assets/RUN_SETUP-login-screen.webp](../assets/RUN_SETUP-login-screen.webp) | Compact remote login-screen evidence after quick install. |

## Screenshot Evidence

![Remote login screen after quick install](../assets/RUN_SETUP-login-screen.webp)

## Timings

| Step | Timing |
|---|---:|
| Docker prerequisite setup | 11 s |
| Quick-install pull/create/start | 41 s |
| App HTTP readiness after Compose start | 41 s |

## Handoff Notes

- Completed: disposable quick install, requested image verification, build/version capture, and remote login-screen access.
- Remaining unfinished coverage: ACC_01 onward.
- Blocked or not applicable: none.
- State left for the next packet: clean installed app with an empty dataset; watched folder `/root/mtl-full-regression-2026-08-13_2028-beta-full-regression/data/gpx`; login screen open in the in-app browser.
