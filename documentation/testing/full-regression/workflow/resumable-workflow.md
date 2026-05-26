# Resumable Full Regression Workflow

Use this workflow for evidence-heavy MTL Explorer full regressions that are too
large for one continuous agent run. The goal is simple: one packet per coverage
ID, saved state, and one final report assembled from packet results.

There is no separate packet catalog. The queue is
`documentation/testing/frontend-regression-test-plan.md`, read top to bottom.

## Sources

- `README.md`: quick-install commands, URLs, credentials, prerequisites, and
  import-folder paths.
- `documentation/testing/frontend-regression-test-plan.md`: coverage IDs,
  coverage text, and packet order.
- `documentation/testing/full-regression/workflow/packet-template.md`: required
  packet result format.

## How To Start

1. Prepare a fresh disposable server with root SSH access.
2. Copy the prompt block from
   `documentation/testing/full-regression/retest-instructions.md`.
3. Replace the server IP and temporary SSH credential placeholders in the copied
   prompt. Do not commit real credentials.
4. Start a Codex Goal, or another long-running agent run, with the filled
   prompt.
5. The first agent action should be creating the run folder, `run-state.md`,
   `packets/`, and `assets/`.

To resume a stopped run, start the agent with:

```text
Resume the full regression from:
documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/run-state.md

Use documentation/testing/frontend-regression-test-plan.md as the coverage-ID
queue. Continue with the next IN PROGRESS, NOT STARTED, PARTIAL, or NOT COVERED
coverage ID. Treat PARTIAL and NOT COVERED as resumable unless Final Assembly
Notes explicitly record that the user approved closing the run with coverage
gaps.
```

## Run Folder

Create one run folder before testing starts:

```text
documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/
  run-state.md
  report.md
  packets/
    RUN_SETUP.md
    DAT_01.md
    IMP_01.md
    TRD_01.md
    MOB_02.md
    RUN_CLEANUP.md
  assets/
    <coverage-id>-<short-name>.webp
    <coverage-id>-<short-name>.txt
```

Use `HHMM` as the run start time in 24-hour local time, for example
`2026-05-25_1430-quick-install-full-regression`.

Only create `report.md` after the finalization gate below passes, or after the
user explicitly approves closing the run with remaining coverage gaps.

## Ordering

- Run `RUN_SETUP` first: install MTL Explorer, record README facts, URLs,
  credentials source, environment, baseline state, and access details.
- Then work coverage IDs from `frontend-regression-test-plan.md` top to bottom.
- Do not skip ahead unless the current ID is `BLOCKED`, `NOT APPLICABLE`, or
  explicitly needs later app state.
- Import/data IDs naturally prepare the shared dataset for later UI IDs.
- Delete IDs run after all IDs that need the full imported dataset.
- Run `RUN_CLEANUP` last: final report, evidence audit, stack shutdown, and
  disposable-directory removal.

## Endless Coverage Gate

The run is intentionally resumable and should keep going across context
compaction, agent restarts, or handoffs. Do not convert unfinished executable
coverage into a terminal result just to produce a report.

### Status Semantics

- Terminal statuses for normal queue advancement: `PASS`, `FAIL`, `BLOCKED`,
  and `NOT APPLICABLE`.
- Resumable statuses: `NOT STARTED`, `IN PROGRESS`, `PARTIAL`, and
  `NOT COVERED`.
- `PARTIAL` means some direct evidence exists, but required child behavior is
  still missing. Leave enough handoff notes for the next agent to continue.
- `NOT COVERED` means no direct execution evidence exists yet. In an active run,
  it is a queue marker, not a completed result.
- Use `BLOCKED` only when a concrete dependency, permission, service, data set,
  browser capability, or environment constraint prevents execution in this run.
  Record what would unblock it.
- Use `NOT APPLICABLE` only when the coverage text genuinely does not apply to
  the configured run, such as plain-HTTP live geolocation checks on a remote
  non-localhost origin.

### Resume Selection

When resuming, pick the first row in queue order whose status is one of:

```text
IN PROGRESS, NOT STARTED, PARTIAL, NOT COVERED
```

If `run-state.md` says `Current coverage ID: COMPLETE` but any row still has one
of those resumable statuses, the state is inconsistent. Correct
`Current coverage ID` and `Next coverage ID` to the first resumable row and
continue.

### Finalization Gate

Before creating `report.md`, setting `Current coverage ID: COMPLETE`, or running
`RUN_CLEANUP`, audit the queue:

- Every coverage ID from `frontend-regression-test-plan.md` must have a packet.
- No packet or run-state row may remain `NOT STARTED`, `IN PROGRESS`,
  `PARTIAL`, or `NOT COVERED`.
- Any unexecuted-but-required item must remain resumable, not be summarized away.
- If an item cannot execute, convert it to `BLOCKED` or `NOT APPLICABLE` with a
  precise reason and unblock path.
- If the user explicitly requests early closure with gaps, record that approval
  in Final Assembly Notes, keep the result `FAIL`, and then cleanup may proceed.

If the gate fails and there is no explicit early-closure approval, do not create
the final report and do not run cleanup. Update `run-state.md` so the next
resumable coverage ID is visible.

Run the gate checker before final report/cleanup:

```bash
documentation/testing/full-regression/workflow/check-finalization-gate.py \
  documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/run-state.md
```

The checker must print `Finalization gate: PASS` for normal cleanup. A failing
checker result means the run is still resumable.

## Coordinator Rules

- The lead/coordinator owns `run-state.md` and final `report.md`.
- Each coverage packet covers exactly one coverage ID.
- Name packet files as `packets/<coverage-id>.md`, for example
  `packets/TRD_01.md`.
- Packet workers only edit their own packet file and matching
  `assets/<coverage-id>-*` files.
- A packet is terminal only when its coverage ID has action, expected result,
  actual result, status, and evidence for `PASS`/`FAIL`, or a clear terminal
  reason for `BLOCKED`/`NOT APPLICABLE`. `PARTIAL` and `NOT COVERED` packets are
  handoff packets and remain resumable during a normal full regression.
- If a packet stops midway, leave its status as `IN PROGRESS` or `BLOCKED` in
  `run-state.md` and include handoff notes in the packet file.

## Run State Template

Use this shape for `run-state.md`:

```markdown
# Full Regression Run State

## Run

| Field | Value |
|---|---|
| Run id | <YYYY-MM-DD_HHMM-short-slug> |
| Target server | <ip-or-host> |
| Source | GitHub main |
| App URL | <remote-url> |
| Started | <timestamp> |
| Coordinator | <agent/name> |

## Shared Facts

- README facts:
- Login credentials source:
- Import folder:
- Browser contexts:
- Known constraints:

## Queue

- Source queue: `documentation/testing/frontend-regression-test-plan.md`
- Current coverage ID:
- Next coverage ID:

Track active, blocked, failed, and recently completed IDs here. Completed packet
files are the durable record.

| Coverage ID | Status | Owner | Packet file | Notes |
|---|---|---|---|---|
| RUN_SETUP | NOT STARTED | | packets/RUN_SETUP.md | |
| DAT_01 | NOT STARTED | | packets/DAT_01.md | |

## Issues

| ID | Severity | Coverage ID | Summary | Status |
|---|---|---|---|---|

## Final Assembly Notes

- Missing coverage IDs:
- Cleanup state:
- Final report path:
- Finalization gate:
- Early closure approval:
```

Use statuses consistently: `NOT STARTED`, `IN PROGRESS`, `PASS`, `PARTIAL`,
`FAIL`, `BLOCKED`, `NOT COVERED`, or `NOT APPLICABLE`. For queue advancement,
only `PASS`, `FAIL`, `BLOCKED`, and `NOT APPLICABLE` are terminal unless the
user approved early closure with gaps.

## Final Report

The final report must keep the existing full-regression format:

- First line: `> **RESULT: PASS - <one concise reason>**` or
  `> **RESULT: FAIL - <one concise reason>**`.
- Include goal, scope, environment, README facts, setup/install result, timings,
  coverage-ID matrix, issues, evidence, cleanup, blocked/untested areas, and
  conclusion.
- Assemble the coverage matrix by coverage ID from packet files only. Do not
  invent final statuses from memory.
- Use `PASS` only if quick install succeeds, required coverage runs, cleanup
  succeeds, and no blocking/high-severity failures remain.
- Do not assemble a final report for a normal run while any coverage ID is still
  `NOT STARTED`, `IN PROGRESS`, `PARTIAL`, or `NOT COVERED`. Continue or leave
  the run resumable instead. A gap report is allowed only after explicit
  user-approved early closure and must be `FAIL`.
