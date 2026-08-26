# Packet: APP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_04
- In scope: Selected UI theme persists across reload and login.

## Prerequisites

- Required previous coverage IDs or run packets: APP_03.
- Required app/data state: Light theme at start; synchronized 8-track session.
- Required browser context: Admin Preferences/Session and login.

## Allowed Mutations

- Allowed: Select Dark; reload; credentials-only sign out/in.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_04 | Selected Dark, reloaded Preferences, signed out/in, and rechecked Preferences. | Theme persists across reload and login. | Root and pressed state stayed Dark through route reload, login page, authenticated map, and reopened Preferences. | PASS | [assets/APP_04-theme-persistence.txt](../assets/APP_04-theme-persistence.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_04-theme-persistence.txt](../assets/APP_04-theme-persistence.txt) | Exact root/pressed state across reload/logout/login. |

## Screenshot Evidence

Direct DOM state is durable; ACC_04 prevents saved screenshots.

## Timings

| Step | Timing |
|---|---:|
| Preferences route reload | About 0.8 s |
| Logout/relogin | About 1.25 s |

## Handoff Notes

- Completed: Dark persistence across reload and login.
- Remaining unfinished coverage: None for APP_04.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Admin Preferences open; dark theme selected and persisted.
