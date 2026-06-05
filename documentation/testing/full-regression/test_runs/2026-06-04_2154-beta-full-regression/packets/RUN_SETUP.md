# Packet: RUN_SETUP

## Scope

- Coverage source: `documentation/testing/full-regression/retest-instructions.md`
- Coverage ID or run packet: RUN_SETUP
- In scope: Fresh disposable quick install on target server, Docker prerequisite setup, beta image override, URL/login verification, baseline environment facts.
- Out of scope: Product source changes, source builds, non-README credentials.

## Prerequisites

- Required previous coverage IDs or run packets: none
- Required app/data state: Fresh target server with root SSH access.
- Required browser context: Desktop Chromium browser context at 1366x900.

## Allowed Mutations

- Allowed: Install missing Docker/Compose prerequisites, create disposable run directory, rotate forced expired root password, install a temporary run-specific SSH key, start quick-install stack, login with README credentials.
- Not allowed: Modify product source, merge GitHub state, use non-README GUI credentials, clean up before finalization gate.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_SETUP | Installed missing Docker prerequisites on Debian 13; created `/root/mtl-regression-2026-06-04_2154-beta-full-regression/mtl-explorer`; downloaded README `docker-compose.yml`; added `.env` override `MTL_APP_IMAGE=wauwau0977/mytraillog:beta`; ran `docker compose up -d`; verified `http://127.0.0.1:18080/mtl/` and `http://167.233.16.201:18080/mtl/`; logged in with README credentials `mtl` / `change-me`. | Stack starts in an isolated directory, app image is `wauwau0977/mytraillog:beta`, server-local and remote URLs return the app, README login reaches the map. | Docker Engine `26.1.5+dfsg1` and Compose `2.26.1-4` installed as missing prerequisites; Compose started app/db/brouter/location-search; app image is beta; local and remote URL returned HTTP 200; browser login reached the map with an empty dataset (`0 Tracks`). | PASS | [assets/RUN_SETUP-compose.txt](../assets/RUN_SETUP-compose.txt); [assets/RUN_SETUP-browser.txt](../assets/RUN_SETUP-browser.txt); [assets/RUN_SETUP-login.webp](../assets/RUN_SETUP-login.webp); [assets/RUN_SETUP-map.webp](../assets/RUN_SETUP-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_SETUP-compose.txt](../assets/RUN_SETUP-compose.txt) | Docker/Compose versions, image list, container status, local URL, import folder, startup log excerpt. |
| [assets/RUN_SETUP-browser.txt](../assets/RUN_SETUP-browser.txt) | Browser redirect/login/map evidence, console and failed request excerpt. |
| [assets/RUN_SETUP-login.webp](../assets/RUN_SETUP-login.webp) | Signed-out login screen after redirect. |
| [assets/RUN_SETUP-map.webp](../assets/RUN_SETUP-map.webp) | Post-login empty map state. |

## Screenshot Evidence

![Login screen](../assets/RUN_SETUP-login.webp)

![Post-login map](../assets/RUN_SETUP-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Docker prerequisite install | 9 seconds |
| Compose pull/create/start | 33 seconds |
| App ready after Compose start | 12 seconds |
| Browser login to map | 9.3 seconds |

## Handoff Notes

- Completed: Quick install is running on `http://167.233.16.201:18080/mtl/` with beta app image and empty data set.
- Remaining unfinished coverage: Continue with `ACC_01`.
- Blocked or not applicable: Remote plain HTTP origin will make live browser geolocation rows not applicable unless localhost/HTTPS is introduced.
- State left for the next packet: Stack remains running; import folder is `/root/mtl-regression-2026-06-04_2154-beta-full-regression/mtl-explorer/data/gpx/`; temporary SSH key is `/tmp/mtl-regression-20260604-2154_ed25519` on the coordinator machine.
