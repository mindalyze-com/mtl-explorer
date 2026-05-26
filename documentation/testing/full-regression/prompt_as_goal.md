# Codex Goal Launcher

## Problem

The full MTL Explorer regression instructions are too long for Codex Goal's
prompt limit. Agents also tend to stop midway, compress the checklist, or pick
only the parts they consider most important when the run is not resumable.

## Approach

Use this file only as the short Goal launcher. Keep the detailed instructions in
`documentation/testing/full-regression/retest-instructions.md` and have the Goal
read them from the repository.

- Split the run into one packet per coverage ID.
- Save packet results and evidence as files.
- Track progress in `run-state.md`.
- Assemble the final report only from completed packet files.

Codex Goal's job is the coordinator loop: pick the next packet, run it, save
state, and continue or resume later.

## How To Start

1. Prepare a fresh disposable server with root SSH access.
2. Start Codex Goal from the repository root.
3. Copy only the **Goal Prompt** below into Codex Goal.
4. Replace the server and temporary SSH credential placeholders. Do not commit
   real credentials.
5. The agent should read the referenced files, create a run folder named
   `documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/`
   with `run-state.md`, `packets/`, and `assets/`, then work through the coverage
   IDs in `frontend-regression-test-plan.md` top to bottom.

To resume a stopped run, start Codex Goal with:

```text
Resume the full regression from:
documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/run-state.md

Use documentation/testing/frontend-regression-test-plan.md as the coverage-ID
queue. Continue with the next IN PROGRESS, NOT STARTED, PARTIAL, or NOT COVERED
coverage ID. Treat PARTIAL and NOT COVERED as resumable unless Final Assembly
Notes explicitly record that the user approved closing the run with coverage
gaps.
```

## Goal Prompt

```text
This is a long task. Use the resumable full-regression workflow.

Use documentation/testing/full-regression/retest-instructions.md as the main prompt.
Use documentation/testing/full-regression/workflow/resumable-workflow.md for coordinator rules.
Use documentation/testing/full-regression/workflow/packet-template.md for packet results.
Use documentation/testing/frontend-regression-test-plan.md as the coverage-ID queue.

Target:
- Server: <server-ip-or-host>
- SSH user: root
- SSH credential/access note: <temporary credential>

Treat the Target section above as the replacement values for the server and SSH
credential placeholders in retest-instructions.md.

Create a run folder named
documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/.
Inside it create run-state.md, packets/, and assets/ before testing.

Work one coverage ID per packet, top to bottom. After each packet, save the
packet file and update run-state.md. Finish only after all required coverage IDs
are terminal (`PASS`, `FAIL`, `BLOCKED`, or `NOT APPLICABLE`), final report.md
is assembled from packet files, and cleanup is verified.

Before report.md, Current coverage ID: COMPLETE, or RUN_CLEANUP, enforce the
endless coverage gate from resumable-workflow.md: no coverage ID may remain NOT
STARTED, IN PROGRESS, PARTIAL, or NOT COVERED unless the user explicitly
approved closing the run with gaps. Run
documentation/testing/full-regression/workflow/check-finalization-gate.py against
the run-state file and require Finalization gate: PASS before normal cleanup. If
the gate fails, keep the run resumable and continue with the first unfinished
coverage ID.
```
