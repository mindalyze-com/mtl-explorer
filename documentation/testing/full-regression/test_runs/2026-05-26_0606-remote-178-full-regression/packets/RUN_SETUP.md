# Packet: RUN_SETUP

## Scope

- Coverage source: `documentation/testing/full-regression/retest-instructions.md`
- Coverage ID or run packet: RUN_SETUP
- In scope: README quick install, SSH prerequisites, URL readiness, README facts, login baseline, empty dataset baseline.
- Out of scope: Product source edits, source builds, non-README workarounds.

## Prerequisites

- Required previous coverage IDs or run packets: none.
- Required app/data state: fresh disposable install directory on target.
- Required browser context: desktop browser, signed out then signed in with README credentials.

## Allowed Mutations

- Allowed: create disposable install directory, download README compose file, start compose stack.
- Not allowed: source-code changes, global Docker prune, unrelated container removal.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| RUN_SETUP | Connected over SSH, verified Docker/Compose, followed README quick start in `/root/mtl-full-regression-2026-05-26_0606/mtl-explorer`, verified local and remote app URLs, opened login and signed in with README credentials. | Docker prerequisites are present or installed separately, app starts from GitHub `main` compose file, `http://localhost:18080/mtl/` and browser URL work, empty baseline is visible. | Debian 13 target already had Docker 29.5.2 and Compose v5.1.4; port `18080` was free; compose stack started in 13s; local and remote URLs returned HTTP 200 with title `MTL Explorer`; signed-in baseline showed `0 Tracks`. | PASS | `assets/RUN_SETUP-ssh-prereq.txt`, `assets/RUN_SETUP-quick-install.txt`, `assets/RUN_SETUP-readiness.txt`, `assets/RUN_SETUP-login.webp`, `assets/RUN_SETUP-empty-map.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/RUN_SETUP-ssh-prereq.txt | Target OS, Docker/Compose versions, and initial port/container state. |
| assets/RUN_SETUP-quick-install.txt | README quick-install command result and compose services. |
| assets/RUN_SETUP-readiness.txt | Local/remote HTTP readiness and data directories. |
| assets/RUN_SETUP-login.webp | Login screen baseline. |
| assets/RUN_SETUP-empty-map.webp | Signed-in empty map baseline. |

## Timings

| Step | Timing |
|---|---:|
| Quick install / compose start | 13s |
| App readiness after compose start | 5s |

## Handoff Notes

- Completed: fresh quick install, README facts, login, and empty map baseline.
- Remaining unfinished coverage: start coverage queue at `ACC_01`.
- Blocked or not applicable: none for setup.
- State left for the next packet: app running at `http://178.105.173.254:18080/mtl/`, compose directory `/root/mtl-full-regression-2026-05-26_0606/mtl-explorer`, browser signed in as README user.
