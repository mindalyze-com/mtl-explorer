---
name: mtl-fix-regression-bug
description: Claim, implement, and locally verify the earliest open MTL Explorer finding from the latest full-regression run, then update its source packet with fix evidence while the suite may continue. Use for product defects recorded by the MTL full-regression workflow; do not use for running the suite, general debugging, deployment, or GitHub issue work.
---

# MTL Fix Regression Bug

Fix one recorded full-regression defect end to end without waiting for the run to finish. Preserve the active regression queue and all unrelated worktree changes.

## Find And Claim The Bug

1. Read `AGENTS.md` and the current finding-status rules in `documentation/testing/full-regression/workflow/resumable-workflow.md`. Inspect `git status` before editing.
2. Use a run or finding named by the user. Otherwise, search `documentation/testing/full-regression/test_runs/` and select the latest full-regression run by the timestamp in its directory name and run metadata. Do not use filesystem modification time. Do not fall back to an older run merely because it has an open issue. If the latest run has no usable `run-state.md` or no `OPEN` finding, report that and stop without changing an older run.
3. In the selected run's `run-state.md` `## Issues` table, select the first row in file order whose finding status is exactly `OPEN`. This is report order, not severity order. Skip `FIX_IN_WORK` and terminal findings.
4. Resolve the row's Coverage ID to `packets/<coverage-id>.md`. Treat that packet's matching Issues row as the source bug record. Read its reproduction, expected and actual behavior, original evidence, scope, allowed mutations, and handoff state.
5. Re-read `run-state.md` immediately before claiming. If the selected row is no longer `OPEN`, do not overwrite it; repeat selection within the same latest run. Claim the finding with a narrow context patch that changes its run-state status to `FIX_IN_WORK`. Set the matching packet finding status to `FIX_IN_WORK`; if an older packet lacks the current `Finding status` column, upgrade only its Issues table to the current packet schema. Immediately tell the user which finding and packet were claimed.
6. Keep the regression suite running. Do not change Current coverage ID, Next coverage ID, unrelated queue rows, finalization state, or cleanup state. Do not create `report.md` for an active run. Re-read files before every later status edit because the suite may update them concurrently.

Work on one finding by default. A second `OPEN` finding may join the task only after reproduction or source analysis shows the same root cause, the same implementation change resolves it, and the same focused retest can prove it. Similar UI area or wording is not enough. Claim every joined finding before changing code for it, and keep separate evidence links and source-packet updates.

## Diagnose And Implement

- Inspect the original evidence before changing product code. Reproduce the recorded path when practical and distinguish a product defect from stale data, test setup, or an already-fixed worktree.
- Do not publish a speculative root cause. Establish it from the execution path, state flow, or failing test, then explain it in the completed source record and final report.
- Make the smallest complete product fix. Preserve existing user changes and avoid unrelated cleanup. Follow the repository's server-first OpenAPI workflow for API shape changes and keep advanced calculations on the server.
- Add or strengthen a focused automated regression test when it can meaningfully prevent recurrence. Run the affected client or server tests plus any broader check justified by the change.
- Do not commit, deploy, publish, or mutate GitHub state unless the user separately requests that action. The repository finding is the origin bug for this workflow.

## Verify Through A Local Dev Server

Direct local application verification is required in addition to code review or unit tests.

- Inspect current project commands and configuration rather than relying on remembered commands. Build and serve the exact current worktree with the needed backend, client, database, and sidecars.
- Prefer an already-running local dev stack only after proving it serves the current changes. Otherwise use unused local ports and isolated state; never stop an unknown process or disturb the active regression target.
- Use GUI credentials only from `mtl-server/src/main/resources/application-dev.yml`. Do not copy credentials, tokens, cookies, private paths, or full environment output into evidence.
- Use public or fully synthetic fixtures. Never read, copy, transform, or derive fixtures from local private GPX tracks or media.
- For a GUI finding, load and use the available `browser:control-in-app-browser` skill. Repeat the source packet's reproduction path through the UI, verify the expected behavior, inspect relevant requests and browser console errors, and capture the repaired state. API evidence alone is insufficient for a GUI defect.
- For a server-only finding, exercise the real local endpoint or background flow and capture the observable result. A mocked unit test alone is insufficient.
- Record the current commit, relevant dirty-worktree note, dev URLs, build identity when available, focused test commands and exit results, reproduction steps, observed fixed behavior, and console/network or server-log outcome.

Store compact fix evidence in the originating run's `assets/` directory. Prefer:

- `<finding-id>-fix-local.txt` below 5 KB for commands and observed results.
- `<finding-id>-fix-local.webp` at or below 85 KB for meaningful GUI evidence.

Use additional assets only when one file cannot prove the behavior. Keep evidence concise, redact secrets and private data, and do not save bulky traces.

## Close The Source Bug

Mark a finding `FIXED` only after focused automated checks pass and a direct local dev-server retest proves the recorded expected behavior.

1. Re-read `run-state.md` and every source packet to preserve concurrent suite updates.
2. Update the packet's matching issue row to `FIXED`, replace or supplement Actual with the local fixed result, and add the new evidence links without removing original failure evidence.
3. Add a concise Fix Record to the packet covering the verified root cause, implementation, automated tests, local dev-server retest, evidence, and any release or deployment boundary. Add the new files to Evidence Files and embed a useful WebP under Screenshot Evidence.
4. Change the matching run-state finding row from `FIX_IN_WORK` to `FIXED` and link or name the source-packet evidence in the relevant run-state notes.
5. Change the packet action and run-state coverage result to `FIXED` only when the selected finding was the sole reason for its failure and the old evidence plus direct retest now proves the complete coverage requirement. Otherwise leave the coverage status unchanged; finding status and coverage status are separate.
6. If an existing final report already mirrors the finding, update that entry and its evidence link. Do not assemble or finalize a report merely to close the finding.
7. Re-run relevant checks, use `git diff --check`, and inspect the final diff for accidental changes to the active run.

If direct verification fails or remains blocked, do not claim `FIXED`. Continue while safe progress is possible. If the task ends without a terminal finding result, record the implementation or blocker in the source packet and return the finding to `OPEN` so it is not stranded as owned work. Use `REJECTED` or `NOT REPRODUCIBLE` only when direct evidence meets the full-regression status rules.

## Report To The User

Lead with the result and include:

- Finding ID or closely related IDs and final statuses.
- Confirmed root cause and the implemented change.
- Automated checks and local dev-server reproduction result.
- Clickable source-packet and fix-evidence paths.
- Any remaining release or deployment step; a local fix is not proof that the regression target image contains it.

Leave code, tests, packet updates, and evidence uncommitted unless the user asks for a commit.
