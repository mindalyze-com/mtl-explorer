# Packet: RUN_SETUP

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: RUN_SETUP
- In scope: GitHub `main` quick-install facts, SSH prerequisites, disposable Compose installation, required image override, image/build verification, and app reachability.
- Out of scope: Product source inspection, source builds, and non-disposable server changes.

## Prerequisites

- Required previous coverage IDs or run packets: None.
- Required app/data state: Fresh disposable server directory.
- Required browser context: Desktop browser after the app starts.

## Allowed Mutations

- Allowed: Create the isolated quick-install directory, download public Compose source, set `MTL_APP_IMAGE`, start the disposable stack, and create test data beneath that directory.
- Not allowed: Change unrelated services or data, prune Docker globally, or change the root password without user authorization.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_SETUP | Read the public GitHub `main` README and Compose facts; verified the unused public port; installed the missing Docker prerequisite; created the isolated install; set `MTL_APP_IMAGE` in `.env` before the first start; ran the documented Compose start; verified local and remote URLs, valid login, effective/running image, digest, and reported build/version. | The fresh quick install starts with the required beta image, is reachable locally and remotely, and accepts only the README-documented login. | PASS. Docker Engine 29.7.2 and Compose v5.5.0 were installed because Docker was absent. The stack started in 31 s and became HTTP-ready 12 s later. The configured and running app reference is `wauwau0977/mytraillog:beta`; its ID/digest is `sha256:eb68ce2b4de68fdbad0357ae11b9446c6dbd2e2a784048e5c29351fdc67b5546`. The About UI reports `dev`; server logs report `0.0.1-SNAPSHOT`, image `1.351`, built 2026-08-17T20:26:28Z. Local and remote URLs returned content and the valid login reached the empty map. | PASS | [assets/RUN_SETUP-install.txt](../assets/RUN_SETUP-install.txt); [assets/RUN_SETUP-browser.txt](../assets/RUN_SETUP-browser.txt); [assets/RUN_SETUP-public-endpoint.txt](../assets/RUN_SETUP-public-endpoint.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/RUN_SETUP-ssh-access.txt](../assets/RUN_SETUP-ssh-access.txt) | SSH prerequisite result and confirmation that no server command ran. |
| [assets/RUN_SETUP-public-endpoint.txt](../assets/RUN_SETUP-public-endpoint.txt) | Command-line and browser baseline showing the public app endpoint refused connections before installation. |
| [assets/RUN_SETUP-install.txt](../assets/RUN_SETUP-install.txt) | Docker prerequisite, quick-install timing, effective image, running image ID/digest, readiness, and build/version evidence. |
| [assets/RUN_SETUP-browser.txt](../assets/RUN_SETUP-browser.txt) | Remote signed-out, valid-login, empty-map, and About UI evidence. |

## Screenshot Evidence

No screenshot yet; the app has not been installed or started.

## Timings

| Step | Timing |
|---|---:|
| Workflow and public source review | Completed before setup |
| SSH prerequisite attempt | 1.3 s |
| Docker prerequisite installation | 18 s |
| `docker compose up -d` | 31 s |
| Application readiness after Compose returned | 12 s |

## Handoff Notes

- Completed: Frozen run initialization and public quick-install fact review.
- Completed: Browser context prepared and public-port baseline captured; no pre-existing app is reachable on port 18080.
- Completed: Downloaded and structurally checked the five suggested public GPX files and the public FIT fixture locally, without treating this as import evidence. Preflight details are in [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt).
- Remaining unfinished coverage: None for RUN_SETUP.
- Blocked or not applicable: Browser screenshot capture timed out; DOM and HTTP evidence were saved, and screenshot capture remains a requirement for ACC_04.
- State left for the next packet: Fresh stack running, signed-in browser on the empty main map, no imported tracks or media.
