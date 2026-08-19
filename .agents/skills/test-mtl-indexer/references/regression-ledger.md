# MTL Explorer Indexer Two-Pass Regression Plan

## Objective

Prove that the GPX and media indexers reach the same correct state through both automatic paths:

1. **Online pass:** filesystem changes are detected by live watchers while the server stays running.
2. **Offline pass:** filesystem changes happen while the server is stopped and are detected by startup reconciliation after it restarts.

Both passes are required. Use an independent disposable database and independent watch roots for each pass. Use the same fixture roles, mutation order, and expected counts so results are comparable.

Do not batch the offline phases into one shutdown. Restart and verify after each mutation phase. Otherwise an intermediate change can be hidden by a later change that produces the same final filesystem state.

## Fixed Fixture Roles And Counts

Create one synthetic GPX and one synthetic embedded-GPS media file for every role. Give every GPX a distinct track name and every media file distinct metadata or coordinates that can be verified in the GUI or API.

| Role | Purpose | First active in phase |
|---|---|---|
| `B1`, `B2` | Baseline files present before first startup | Baseline |
| `A1`, `A2`, `A3` | First added batch | Add |
| `N1` | New file added after deletion | Add again |
| `A2-backup` | Exact backup kept outside the watch root | Same-path restore |
| `B1-replacement` | Different content atomically installed at the `B1` path | Active replacement |

Expected active GPX/media counts in each pass:

```text
Baseline:           2 / 2
Add A1+A2+A3:       5 / 5
Delete A2:          4 / 4
Add N1:             5 / 5
Restore A2:         6 / 6
Replace B1 content: 6 / 6
```

The active replacement must change the GPX track name and the media metadata or coordinates without changing either active count.

## Common Preparation

1. Read repository instructions and inspect the current worktree, indexer changes, startup commands, properties, and focused tests.
2. Build the exact current worktree.
3. Create deterministic synthetic fixtures for every role. Never use or derive data from private GPX tracks or media.
4. Record a fixture manifest with category, role, filename, byte size, SHA-256, track name or media metadata, timestamps, coordinates, and synthetic provenance.
5. Keep immutable fixture sources and deletion backups outside all watched roots.
6. Allocate separate temporary roots, database state, server logs, and unused ports for the online and offline passes.
7. Enable GPX and media live watch for the isolated online pass. Record all configuration overrides.
8. Prepare the browser login from the repository's development configuration. Do not record credentials in evidence.
9. Create the evidence directory and the phase ledger before starting either pass.

## Pass 1: Server Running — Live-Watch Flow

The server remains running from Step 1.2 through Step 1.8. Do not invoke a manual rescan.

### 1.1 Prepare the online baseline

1. Start with a fresh disposable online database and empty online watch roots.
2. Copy `B1` and `B2` GPX files into the GPX root.
3. Copy `B1` and `B2` media files into the media root.
4. Record the filesystem state and expected count `2 / 2`.

### 1.2 Start and verify the online baseline

1. Start the database and server.
2. Wait for both initial scans to complete, both live watchers to start, and dependent processing to settle.
3. Verify one active index entry per baseline path and successful domain imports.
4. Verify `2` active tracks and `2` active media items through direct server or database evidence.
5. Open the GUI, log in, and verify `2 / 2`, both track names, and both media items.
6. Capture baseline text evidence and a compact WebP screenshot.

### 1.3 Add the first batch while running

1. Record the mutation time.
2. Atomically place `A1`, `A2`, and `A3` into both watched roots.
3. Verify live-watch events for all six paths without a manual scan.
4. Wait for index and dependent processing to settle.
5. Verify `5` active tracks and `5` active media items, with no duplicate domain rows.
6. Verify the GUI notices new data, use its supported Reload action, and confirm `5 / 5` plus the new items.
7. Record mutation-to-settlement time and capture evidence.

### 1.4 Delete an indexed pair while running

1. Copy the exact `A2` GPX and media files to the backup area outside the watch roots.
2. Record their checksums and the deletion time.
3. Delete both watched `A2` paths.
4. Verify automatic removal handling for both paths without a manual scan.
5. Verify the `A2` domain rows are removed, their index state records removal according to the current model, and remaining paths are unchanged.
6. Verify `4` active tracks and `4` active media items.
7. Verify the GUI notices the change, Reload shows `4 / 4`, and the deleted track/media are absent.
8. Capture evidence.

### 1.5 Add new files again while running

1. Record the mutation time.
2. Atomically place the new `N1` GPX and media pair into the watched roots.
3. Verify both live-watch additions and successful processing.
4. Verify `5 / 5`, one active row per path, and no reactivation of `A2`.
5. Verify the GUI freshness flow and the presence of `N1` after Reload.
6. Capture evidence.

### 1.6 Restore the deleted paths while running

1. Record the mutation time.
2. Restore both exact `A2` backups to their original watched paths through atomic rename.
3. Verify both existing index records are invoked again or otherwise reused according to the current model; do not accept duplicate path records.
4. Verify `6 / 6` and the restored domain content.
5. Verify the GUI freshness flow and the restored items after Reload.
6. Capture evidence.

### 1.7 Replace active content while running

1. Record the original `B1` checksums and domain identifiers.
2. Atomically replace the active `B1` GPX path with `B1-replacement` containing a different track name.
3. Atomically replace the active `B1` media path with `B1-replacement` containing different verifiable metadata or coordinates.
4. Verify live-watch replacement processing for both paths.
5. Verify the old GPX domain content is absent, the replacement appears exactly once, and the media row refreshes or replaces cleanly according to the current model.
6. Verify counts stay `6 / 6` with no duplicate path or domain rows.
7. Verify the GUI freshness flow, replacement content, and stable counts.
8. Capture evidence.

### 1.8 Finish the online pass

1. Verify GPS, MEDIA, and dependent jobs are settled and successful.
2. Audit server errors, failed requests, and browser console errors for the complete online pass.
3. Record the online-pass result. Any required manual rescan makes the affected phase FAIL or PARTIAL.
4. Stop the online server and database gracefully.
5. Verify the process is absent and both ports are closed before starting the offline pass.

## Pass 2: Server Stopped — Startup-Catch-Up Flow

Use fresh disposable state. Repeat the same fixture roles, mutations, order, and active counts. For every mutation phase: stop, prove stopped, mutate, prove the database has not changed while stopped, restart, and verify automatic startup reconciliation. Do not invoke a manual rescan.

### 2.1 Prepare and verify the offline baseline

1. Start with a fresh disposable offline database and empty offline watch roots.
2. Place `B1` and `B2` GPX and media pairs in the roots.
3. Start the database and server.
4. Wait for initial scans, watchers, and dependent processing to settle.
5. Verify `2 / 2` through server/database evidence and the GUI.
6. Capture baseline evidence.

### 2.2 Add the first batch while stopped

1. Stop the server gracefully. Keep the disposable database available for before/after evidence if its design permits.
2. Prove the server process is absent and its port is closed. Record shutdown time.
3. Snapshot the index and domain counts: `2 / 2`.
4. While the server is stopped, atomically place `A1`, `A2`, and `A3` into both watched roots.
5. Record mutation time and filesystem checksums. Confirm persisted application state is still `2 / 2` before restart.
6. Restart the server. Record restart time.
7. Verify startup scan or reconciliation detects all six additions without a manual scan.
8. Wait for processing to settle and verify `5 / 5`, unique path rows, and no duplicates.
9. Open or reconnect the GUI. Its first successful post-restart state must show `5 / 5` and the new items; it must not remain on stale cached data.
10. Capture shutdown, mutation, restart, settlement, and GUI evidence.

### 2.3 Delete an indexed pair while stopped

1. Back up the exact watched `A2` files outside the roots and record checksums.
2. Stop the server and prove the process and port are inactive.
3. Snapshot persisted active counts: `5 / 5`.
4. While stopped, delete both watched `A2` paths.
5. Record deletion time and confirm persisted application state remains `5 / 5` before restart.
6. Restart the server.
7. Verify startup reconciliation detects both missing paths and removes their active domain content without a manual scan.
8. Verify `4 / 4`, correct removed index state or invocation history, and no changes to unrelated paths.
9. Verify the first successful GUI state shows `4 / 4` and no `A2` content.
10. Capture evidence.

### 2.4 Add new files again while stopped

1. Stop the server and prove it is inactive.
2. Snapshot persisted active counts: `4 / 4`.
3. While stopped, atomically place the new `N1` pair into the watch roots.
4. Confirm persisted application state remains `4 / 4` before restart.
5. Restart the server and verify startup reconciliation imports both new files without a manual scan.
6. Verify `5 / 5`, unique path/domain rows, and no reactivation of `A2`.
7. Verify the first successful GUI state shows `5 / 5` and `N1`.
8. Capture evidence.

### 2.5 Restore deleted paths while stopped

1. Stop the server and prove it is inactive.
2. Snapshot persisted active counts: `5 / 5`.
3. While stopped, restore both exact `A2` backups to their original watched paths through atomic rename.
4. Confirm persisted application state remains `5 / 5` before restart.
5. Restart the server and verify startup reconciliation reprocesses the restored paths without creating duplicate index records.
6. Verify `6 / 6` and restored domain content.
7. Verify the first successful GUI state shows `6 / 6` and the restored items.
8. Capture evidence.

### 2.6 Replace active content while stopped

1. Stop the server and prove it is inactive.
2. Snapshot persisted active counts: `6 / 6`, plus the original `B1` checksums and domain identifiers.
3. While stopped, atomically replace both active `B1` paths with their replacement content.
4. Confirm persisted counts and old domain content remain unchanged before restart.
5. Restart the server and verify startup reconciliation detects both changed paths without a manual scan.
6. Verify old GPX content is removed, replacement GPX content appears exactly once, and media metadata or location is refreshed cleanly.
7. Verify counts remain `6 / 6` with no duplicate path or domain rows.
8. Verify the first successful GUI state shows the replacements and stable counts.
9. Capture evidence.

### 2.7 Finish the offline pass

1. Verify GPS, MEDIA, and dependent jobs are settled and successful.
2. Audit startup logs for every restart, server errors, failed requests, and browser console errors.
3. Record the offline-pass result. A change found only after a manual rescan is a startup-catch-up failure.

## Targeted Automated Tests

1. Run relevant server indexer, watcher, startup-scan, deletion, replacement, and processing tests.
2. Run relevant client freshness and Reload tests.
3. Record exact commands, duration, and results.
4. Report pre-existing or unrelated failures separately. They do not erase an observed indexer regression.

## Final Cleanup

1. Stop only the disposable offline server and database services.
2. Verify all allocated application and database ports are free.
3. Verify no disposable process or container remains.
4. Close temporary browser tabs.
5. Move temporary fixture and database directories to Trash when practical, and record their recoverability.
6. Leave evidence uncommitted unless the user asks to commit it.

## Phase Evidence Template

Use one concise text record per phase:

```text
Pass: online/offline
Phase:
Detection mode: live-watch/startup-reconciliation
Expected active GPX/media:
Shutdown proof: not-applicable or process/port evidence
Pre-mutation persisted state:
Mutation UTC:
Restart UTC: not-applicable or timestamp
Settled UTC:
Elapsed from mutation:
Filesystem mutation and checksums:
Watcher or startup-reconciliation evidence:
Index rows and invocation counts:
Active domain counts:
Browser freshness or first post-restart state:
GUI state after Reload/reconnect:
Manual rescan used: yes/no
Status: PASS/FAIL/PARTIAL/BLOCKED
```

Keep logs short. Include only lines that prove event detection, startup reconciliation, removal, replacement, successful import, or failure.

## Result Rules

- **PASS:** direct server/database and GUI evidence meets every phase requirement in both passes.
- **FAIL:** behavior is wrong, an automatic live or startup path needs manual intervention, data duplicates, or a high-severity issue remains.
- **PARTIAL:** some direct evidence exists but a required assertion is missing; keep the run resumable.
- **BLOCKED:** a concrete dependency or permission prevents execution; state what unblocks it.

Do not average phases into a broad PASS. The final result is PASS only when both complete passes, targeted tests, the error audit, and cleanup pass.

## Report Structure

Start with exactly one clear result line:

```markdown
> **RESULT: PASS - <concise reason>.**
```

or

```markdown
> **RESULT: FAIL - <concise reason>.**
```

Then include:

1. Scope and exact commit/worktree state.
2. Environment and deliberate configuration overrides.
3. Fixture manifest.
4. Online-pass ledger with expected, actual, status, timing, and evidence links.
5. Offline-pass ledger with shutdown proof, expected, actual, status, timing, and evidence links.
6. Targeted automated-test results.
7. Findings with reproduction, expected/actual, severity, and release impact.
8. Browser/server error audit.
9. Cleanup result and recoverability.
10. Conclusion.
