---
name: test-mtl-indexer
description: Run an evidence-backed local regression of the MTL Explorer GPX and media file indexers, covering live-watch behavior and startup catch-up after offline filesystem changes. Use for end-to-end indexer validation; do not use for unit-test-only requests or unrelated filesystem code.
---

# Test MTL Indexer

Validate the current worktree through the server, database, watcher, startup reconciliation, and GUI. Treat live detection, offline catch-up, and clean domain-row replacement as separate assertions.

## Before Running

- Read the repository's `AGENTS.md` and preserve existing worktree changes.
- Inspect the relevant indexer, processing-worker, freshness-reload, and test diffs. Test the exact current worktree.
- Inspect current startup instructions, property names, fixture generators, and focused/full regression conventions. Do not rely on remembered commands.
- Run the complete mutation flow twice with independent disposable state: once while the server stays running and once while each mutation happens with the server stopped.
- Keep additions, deletion, add-again, same-path restoration, and active-path replacement serialized. Do not combine phases whose final filesystem state could hide an intermediate failure.
- If GUI behavior is in scope, use the available Browser skill and in-app browser. API or database checks alone are not GUI evidence.

## Isolation And Data Safety

- Use only fully synthetic GPX and media. Never read, copy, transform, checksum, or derive fixtures from private tracks or media.
- Keep watched fixtures in a validated `mktemp -d` directory. Keep source backups outside the watched roots.
- Use a fresh disposable database and unused local ports. Do not reuse a personal database.
- Build the current worktree and start a local server against the disposable database and watch roots.
- Prefer the repository's synthetic renderers, such as `photo_placeholder.py` or `generate_regression_photos.py`. If their host dependencies are missing, use an isolated temporary environment or the packaged app generator and record that choice.
- Do not copy generated GPX or media into the repository. Store only checksums, byte sizes, names, timestamps, short logs, screenshots, and the report.
- Media live watch is normally disabled for large trees. When the requested regression requires automatic media detection, explicitly enable it for the isolated run and disclose the override in the report.

Verify the current forms of these properties before using them:

- `mtl.gpx-watch-directory`
- `mtl.media-watch-directory`
- `mtl.indexer.gps.live-watch-enabled`
- `mtl.indexer.media.live-watch-enabled`

## Required Regression Flow

Read [references/regression-ledger.md](references/regression-ledger.md) before execution. Follow its numbered two-pass plan and exact state transitions.

1. Generate deterministic, distinct synthetic fixture pairs and a manifest. Use embedded-GPS media so GUI media visibility does not depend on track correlation.
2. Run the **online pass** against a fresh database and fresh watch roots. Establish the baseline, then perform add, delete, add-again, same-path restore, and active-path replacement while the server stays running. Each phase must settle and pass before the next mutation.
3. Run the **offline pass** against another fresh database and fresh watch roots. Establish the same baseline. Before every mutation phase, stop the server gracefully and prove that its process and port are inactive. Mutate the watched files only while it is stopped, then restart it and verify startup reconciliation before proceeding.
4. Use the same fixture roles, mutation order, and expected active counts in both passes. A PASS in one mode cannot compensate for a failure in the other.
5. Confirm final GPS, MEDIA, and dependent processing jobs are settled. Run the relevant server indexer/processing tests and client freshness-reload tests.
6. Audit server errors, browser console errors, and failed requests. Separate pre-authentication or unrelated sidecar noise from mutation-phase failures.
7. Capture cleanup evidence, stop only the disposable services, verify ports are free, and move the validated temporary directories to Trash when practical.

## Waiting And Failure Rules

- File event detection is not completion. Wait for the observer callback, domain row, index status, and GUI state.
- In the online pass, require live-watch detection. In the offline pass, require startup scan or reconciliation after restart; no live event is expected for a change made while the process was absent.
- Do not batch all offline mutations into one outage. Restart and verify after each phase so additions, removals, restoration, and replacement cannot mask one another.
- Poll with timestamps instead of fixed long sleeps. Watcher stabilization can take several seconds.
- Set a bounded phase timeout appropriate to the local system. A 60-second first bound is reasonable for a small synthetic set; extend only when logs show forward progress.
- If the build fails, startup fails, a watcher does not start, offline modifications are missed after restart, counts diverge, processing stops progressing, the freshness notice does not appear, Reload fails, domain rows duplicate, or cleanup fails, alert the user immediately in commentary. Include the pass, phase, expected state, observed state, and current evidence path. Continue only when a safe diagnostic step remains.
- If manual rescan is required after a requested automatic-watch phase, record the automatic phase as FAIL or PARTIAL. A successful rescan does not convert it to PASS.
- Apply the same rule to offline catch-up: a manual rescan after restart does not convert a failed startup-reconciliation phase to PASS.
- Do not mark a phase PASS from logs alone. Require direct state evidence and GUI evidence when GUI behavior is in scope.

## Evidence And Result

- Create a focused run under `documentation/testing/indexer-regression/test_runs/<YYYY-MM-DD_HHMM-indexer-focused>/` unless the user requests another location.
- Keep one concise `.txt` evidence file and one compact WebP screenshot for each meaningful GUI phase. Keep synthetic files out of the repository.
- Record the pass and detection mode, shutdown proof for offline phases, mutation/restart/settled times, filesystem names, index status/invocation count, active domain counts, relevant log lines, GUI counts/names, and whether a rescan was used.
- Prefer screenshots at or below 85 KB and text evidence below 5 KB.
- Write a standalone `report.md` with a first-line PASS or FAIL result, environment, phase ledger, timings, findings, linked evidence, cleanup, and conclusion.
- PASS requires every phase in both passes, targeted tests, GUI verification, and cleanup to pass with no blocking or high-severity failure.
- Leave the evidence uncommitted unless the user asks to commit it.
