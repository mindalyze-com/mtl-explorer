# Codex `/goal` Launcher

## Problem

The full MTL Explorer regression has too much detail for one self-contained
launcher prompt. Agents also tend to stop midway, compress the checklist, or
pick only the parts they consider most important when the run is not resumable.

## Approach

Use this file only as the short Goal launcher. Keep the detailed instructions in
`documentation/testing/full-regression/retest-instructions.md` and have the Goal
read them from the repository.

- Initialize the run and frozen coverage queue with the workflow script.
- Split the run into one packet per coverage ID.
- Save packet results and evidence as files.
- Track progress in `run-state.md`.
- Assemble the final report only from completed packet files.

The `/goal` job is the coordinator loop: pick the next packet, run it, save
state, and continue or resume later.

## How To Start

1. Prepare a fresh disposable server with root SSH access.
2. Open Codex at the repository root.
3. Replace the server and temporary SSH credential placeholders. Do not commit
   real credentials.
4. Copy the complete **Goal Prompt** below, including its initial `/goal`, into
   Codex.
5. The agent should run `workflow/init-run.py`, use the printed run folder, and
   work through the frozen coverage IDs in that run's `coverage-plan.md`.

To resume a stopped run, use:

```text
/goal Resume the full regression from:
documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/run-state.md

Use the run's coverage-plan.md as the frozen coverage-ID queue. Continue with
the next IN PROGRESS, NOT STARTED, PARTIAL, or NOT COVERED coverage ID. Treat
PARTIAL and NOT COVERED as resumable unless Final Assembly Notes explicitly
record that the user approved closing the run with coverage gaps.
```

## Goal Prompt

```text
/goal Run one fresh, resumable MTL Explorer quick-install and full end-user
regression. Continue until the frozen coverage queue is terminal, report.md is
assembled from packet files, and cleanup is verified.

Use documentation/testing/full-regression/retest-instructions.md as the main prompt.
Use documentation/testing/full-regression/workflow/resumable-workflow.md for coordinator rules.
Use documentation/testing/full-regression/workflow/packet-template.md for packet results.
Use documentation/testing/frontend-regression-test-plan.md only as the source
that the initializer freezes for this run.

Target:
- Server: <server-ip-or-host>
- SSH user: root
- SSH credential/access note: <temporary credential>
- App image: wauwau0977/mytraillog:beta
- Compose image override: MTL_APP_IMAGE

Treat the Target section above as the replacement values for the server and SSH
credential placeholders in retest-instructions.md. The App image is the required
replacement for the app-image placeholder there.

Before testing, run exactly once:

documentation/testing/full-regression/workflow/init-run.py \
  --server <server-ip-or-host> \
  --ssh-user root \
  --app-image wauwau0977/mytraillog:beta \
  --slug beta-full-regression

Use the run folder printed by the initializer. It contains run-state.md, the
frozen coverage-plan.md, packets/, and assets/. Do not replace its queue with a
newer version of documentation/testing/frontend-regression-test-plan.md during
this run.

During RUN_SETUP, set MTL_APP_IMAGE=wauwau0977/mytraillog:beta in the disposable
Compose installation before the first start. Verify the effective Compose image
and the running app container image. Record the image reference, image ID or
digest, and reported application build/version in RUN_SETUP evidence.

Work one coverage ID per packet, top to bottom. After each packet, save the
packet file and update run-state.md. Finish only after all required coverage IDs
are terminal (`PASS`, `FAIL`, `BLOCKED`, `NOT APPLICABLE`, `FIXED`, `REJECTED`,
`NOT REPRODUCEABLE`, or `NOT REPRODUCIBLE`), final report.md is assembled from
packet files, and cleanup is verified.

Track findings separately with `OPEN`, `FIX_IN_WORK`, `FIXED`, `REJECTED`, or
`NOT REPRODUCIBLE` (`NOT REPRODUCEABLE` is accepted for legacy runs). Use
`FIX_IN_WORK` only when an owner has started implementing, reviewing, or
directly verifying a fix, not for regression investigation or triage alone.

Before report.md, Current coverage ID: COMPLETE, or RUN_CLEANUP, enforce the
endless coverage gate from resumable-workflow.md: no coverage ID may remain NOT
STARTED, IN PROGRESS, PARTIAL, or NOT COVERED unless the user explicitly
approved closing the run with gaps. Run
documentation/testing/full-regression/workflow/check-finalization-gate.py against
the run-state file and require Finalization gate: PASS before normal cleanup. If
the gate fails, keep the run resumable and continue with the first unfinished
coverage ID.
```
