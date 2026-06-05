# Packet: RUN_SETUP

## Scope

- Coverage source: README quick start plus full-regression setup instructions.
- Coverage ID or run packet: RUN_SETUP
- In scope: Target access, Docker prerequisite check, README quick install, beta image override, app URL/login baseline, import folder, baseline app availability.
- Out of scope: Product source inspection or code changes.

## Prerequisites

- Required previous coverage IDs or run packets: None.
- Required app/data state: Fresh disposable install directory on target server.
- Required browser context: Desktop browser after stack startup.

## Allowed Mutations

- Allowed: Create disposable directory on target, download README compose file, set documented environment overrides, start the quick-install stack, rotate the target SSH password because login enforcement required it.
- Not allowed: Modify product source, merge/rebase GitHub branches, store SSH credential in artifacts, globally prune Docker resources.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_SETUP | Installed missing Docker prerequisite, created `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer`, downloaded README `docker-compose.yml`, set `MTL_APP_IMAGE=wauwau0977/mytraillog:beta`, ran `docker compose up -d`, verified local and remote app URL, and logged in with README GUI credentials. | Docker Compose quick-install stack starts, app uses beta image, `http://localhost:18080/mtl/` and `http://167.233.16.201:18080/mtl/` respond, and `mtl` / `change-me` reaches the map. | Docker Engine 29.5.2 and Compose v5.1.4 were installed as missing prerequisites; stack started with app image `wauwau0977/mytraillog:beta` digest `sha256:a42f29e01cf11c343bcd876d0fc2aebf7bcd9334b3cefae31cf8492d773c488b`; local and remote URL returned HTTP 200; browser login reached the map showing 0 tracks and no captured console warning/error entries. | PASS | [assets/RUN_SETUP-docker-install.txt](../assets/RUN_SETUP-docker-install.txt), [assets/RUN_SETUP-compose-up.txt](../assets/RUN_SETUP-compose-up.txt), [assets/RUN_SETUP-app-readiness.txt](../assets/RUN_SETUP-app-readiness.txt), [assets/RUN_SETUP-login-map.webp](../assets/RUN_SETUP-login-map.webp), [assets/RUN_SETUP-browser-console.txt](../assets/RUN_SETUP-browser-console.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_SETUP-target-probe.txt](../assets/RUN_SETUP-target-probe.txt) | Initial target OS/disk/memory probe showing Docker was absent. |
| [assets/RUN_SETUP-docker-prereq-policy.txt](../assets/RUN_SETUP-docker-prereq-policy.txt) | Debian package policy check before Docker setup. |
| [assets/RUN_SETUP-docker-compose-search.txt](../assets/RUN_SETUP-docker-compose-search.txt) | Debian repository search showing no Compose plugin package in default apt sources. |
| [assets/RUN_SETUP-docker-install.txt](../assets/RUN_SETUP-docker-install.txt) | Cropped Docker prerequisite installation and version evidence. |
| [assets/RUN_SETUP-compose-up.txt](../assets/RUN_SETUP-compose-up.txt) | Cropped compose quick-install output, configured images, and container status. |
| [assets/RUN_SETUP-app-readiness.txt](../assets/RUN_SETUP-app-readiness.txt) | App readiness, local/remote HTTP 200, image digest, and startup log summary. |
| [assets/RUN_SETUP-login-map.webp](../assets/RUN_SETUP-login-map.webp) | Browser screenshot after successful README credential login. |
| [assets/RUN_SETUP-browser-console.txt](../assets/RUN_SETUP-browser-console.txt) | Captured setup browser console warnings/errors; empty JSON array. |

## Screenshot Evidence

**Browser screenshot after successful README credential login.**

![Browser screenshot after successful README credential login.](../assets/RUN_SETUP-login-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Docker prerequisite setup | ~6 seconds after apt metadata check |
| Compose pull and stack start | 28 seconds (`15:30:53Z` to `15:31:21Z`) |
| App startup after container start | 14.542 seconds per Spring log |
| HTTP readiness after readiness poll | 9 seconds on first poll run; 1 second on repeat confirmation |
| Browser login baseline | ~6 seconds |

## Handoff Notes

- Completed: Quick install stack is running on the target and browser baseline login succeeded.
- Remaining unfinished coverage: Continue queue at `ACC_01`.
- Blocked or not applicable: Remote plain-HTTP origin will make secure-browser geolocation rows `NOT APPLICABLE` unless a secure origin is introduced; installed-PWA offline row may be `NOT APPLICABLE` if no installed web-app context is used.
- State left for the next packet: App is running with an empty imported dataset; watched import folder is `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/`.
