# Full Regression Quick-Install Prompt

Use this prompt when asking an agent to install MTL Explorer from the README,
run the full end-user regression with the resumable packet workflow, write a
report, and clean up the test server. Replace the placeholders before sending.

````text
Please test the MTL Explorer quick install plus full end-user regression on:

- IP: <server-ipv4>
- SSH user: root
- SSH password/key note: <temporary-credential-or-access-note>

Use GitHub `main` from https://github.com/mindalyze-com/mtl-explorer.

Read these files from that GitHub source before acting:

- `README.md`
- `documentation/testing/frontend-regression-test-plan.md`
- `documentation/testing/full-regression/workflow/resumable-workflow.md`
- `documentation/testing/full-regression/workflow/packet-template.md`

Derive all quick-install commands, app URLs, credentials, prerequisites, and
import-folder paths from the README. Derive regression coverage and packet order
from the coverage IDs in the test plan. Use one packet per coverage ID. Use the
workflow files for state tracking, packet result format, and final report
assembly. Do not use memory or invented defaults; report missing required
details as documentation gaps.

Execution guidance:

- This is a long, evidence-heavy task. Do not run it as one unstructured pass.
  Create a run folder named
  `documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/`,
  instantiate `run-state.md`, then work through the coverage IDs from
  `frontend-regression-test-plan.md`, top to bottom. Use `HHMM` as the 24-hour
  local run start time.
- The lead/coordinator owns `run-state.md` and final `report.md`. Packet workers
  may only write their own `packets/<coverage-id>.md` file and
  `assets/<coverage-id>-*` files.
- If sub-agents or delegated work are available, delegate only one coverage ID
  per packet, with explicit prerequisites and handoff notes. The lead remains
  responsible for the final coverage matrix, statuses, and conclusion.
- Shared-state mutations must be serialized: `RUN_SETUP` first, import IDs
  before UI checks, delete IDs after all checks that need the full imported
  dataset, and `RUN_CLEANUP` last. Read-only UI packets may run in separate
  browser contexts after import is complete.
- If the run pauses or an agent stops, resume from `run-state.md` and the packet
  files. Do not rely on conversation memory.
- On resume, continue with the first `IN PROGRESS`, `NOT STARTED`, `PARTIAL`, or
  `NOT COVERED` coverage ID in queue order. `PARTIAL` and `NOT COVERED` are
  unfinished handoff states during an active run, not reasons to skip ahead.

Install and test:

- Work over SSH in a fresh disposable directory on the target server.
- Follow the README quick-install steps exactly, except for the disposable
  parent directory needed to isolate the run.
- Install only missing Docker prerequisites if needed, and report that setup
  separately from the MTL Explorer result.
- Treat this as a black-box installed-app regression. Do not inspect or change
  product source code, run source builds/tests, start dev servers, or apply
  product workarounds.
- Verify the documented local app URL from the server and the browser-accessible
  remote URL derived from it.
- Use only README-documented login credentials.

Run the full user-facing regression plan:

- Use the frontend regression test plan coverage IDs as the coverage matrix.
- Create one packet file per coverage ID, for example `packets/TRD_01.md`.
  Every packet result must follow `workflow/packet-template.md`.
- Treat every coverage ID in the frontend regression test plan as required
  unless it is explicitly not applicable to the run. Do not collapse an ID
  prefix or chapter into one passing row unless all child IDs were actually
  exercised.
- For every user-facing coverage ID, record action, expected result, actual
  result, status, and evidence.
- A packet is terminal only when its coverage ID has direct evidence for
  `PASS`/`FAIL`, or a concrete terminal reason for `BLOCKED`/`NOT APPLICABLE`.
  `PARTIAL` and `NOT COVERED` must remain resumable unless the user explicitly
  approves closing the run with gaps.
- Test desktop and narrow mobile/touch viewports. Include hard refresh, normal
  reload, back/forward navigation, a clean browser context where useful,
  console errors/warnings, and failed network requests.
- Run offline/cache coverage only in an installed PWA / installed web-app
  browser context after one successful online load. If the app is only opened as
  a normal browser tab, do not fail the row for offline reload behavior; mark the
  installed-PWA offline row `NOT APPLICABLE`, explain that offline mode requires
  browser installation, then restore connectivity and verify normal online
  recovery.

Required data-change coverage:

- Download at least five public internet GPX files with real
  `trk`/`trkseg`/`trkpt` sequences; waypoint-only files are not valid positive
  import evidence.
- Prefer timestamped trackpoints. Record source URL, destination filename,
  checksum, byte size, `trkpt` count, timestamp count, imported id(s), and track
  name(s).
- Import the five GPX files through the documented watched import folder or
  upload UI, wait for indexing, then verify map, track browser, details,
  filters, heatmap, and statistics.
- Import at least one public GPS-bearing FIT activity file and verify conversion
  to displayed track plus **Download original source file** and **Download as GPX**.
- For track details, explicitly open at least one GPX-backed track and one
  FIT-backed track from user-facing navigation. Click through Overview, Graphs,
  Quality, Related, and Events. In Graphs, verify elevation, speed, distance,
  and gain charts plus available graph controls such as time/distance x-axis,
  range band, point-count slider, and graph-height slider. Verify chart hover
  and mini-map hover sync in both directions. Use the visible UI controls for
  **Download original source file** and **Download as GPX** and record evidence.
- Delete two imported source files from the documented watched/import folder,
  wait for processing or trigger the documented rescan action, then verify the
  map, browser, filters, heatmap, stats, and details reflect removal.

Strict result handling:

- Do not pass a row just because a dependency, permission, sidecar, internet
  service, or data source is unavailable. Use `BLOCKED` or `NOT APPLICABLE` for
  true terminal constraints, explain why, state what would unblock it, and state
  whether it blocks the full regression.
- Do not pass a parent area when any child coverage ID is skipped,
  spot-checked only, or verified indirectly. Use `PARTIAL` as a resumable
  handoff state and name the missing child checks. Use `BLOCKED` or
  `NOT APPLICABLE` only for true terminal constraints.
- Do not mark executable coverage `NOT COVERED` merely because it was not reached
  yet. Leave it resumable and continue. If there is no direct execution
  evidence, do not run cleanup or call the queue complete.
- Assign findings IDs and severities: `P0`, `P1`, `P2`, or `P3`.
- For each issue, include reproduction steps, expected/actual result,
  environment, evidence, and release impact.
- Record timings for Docker setup, quick install, container startup, import
  sync, deletion sync, desktop regression, mobile regression, offline/cache,
  final verification, and cleanup.

Report and evidence:

- Write a standalone Markdown report, not a transcript.
- Assemble the final report only from completed packet files and linked assets.
  Do not invent final statuses from memory or broad impressions.
- Before writing `report.md`, setting `Current coverage ID: COMPLETE`, or running
  `RUN_CLEANUP`, enforce the finalization gate from
  `workflow/resumable-workflow.md`: every coverage ID must be terminal
  (`PASS`, `FAIL`, `BLOCKED`, or `NOT APPLICABLE`) and no packet/run-state row
  may remain `NOT STARTED`, `IN PROGRESS`, `PARTIAL`, or `NOT COVERED`.
- Run
  `documentation/testing/full-regression/workflow/check-finalization-gate.py <run-folder>/run-state.md`
  and require `Finalization gate: PASS` before normal report/cleanup.
- If the finalization gate fails, update `run-state.md` with the first
  resumable coverage ID and continue testing instead of assembling a gap report
  or cleaning up. A gap report is allowed only if the user explicitly approves
  early closure with remaining gaps; that report must be `FAIL`.
- First line must be:
  `> **RESULT: PASS - <one concise reason>**` or
  `> **RESULT: FAIL - <one concise reason>**`.
- Use `PASS` only if quick install succeeds, required regression coverage runs,
  cleanup succeeds, and no blocking/high-severity failures remain.
- Include goal, scope, environment, extracted README facts, setup/install result,
  timings, coverage-ID matrix, issues, evidence, cleanup,
  blocked/untested areas, and conclusion.
- Save the report at
  `documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/report.md`.
- Save packet results under
  `documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/packets/`.
- Save screenshots/log snippets under the matching `assets/` folder. Prefer WebP
  screenshots, keep logs short, and avoid bulky traces unless needed for a
  failure.
- Keep compact screenshots for working functions as well as failures, so the
  report gives a useful visual overview of validated areas such as login, map,
  imports, browser, stats, filters, details, admin, planner, mobile, and
  deletion sync.
- Embed relevant passing and failing screenshots inline in the Markdown report
  with image syntax, not only as asset links, so the report is readable on its
  own.
- Save attached log files with `.txt` filenames, never `.log`, because `.log`
  files are intentionally ignored by Git.
- Keep each attached log `.txt` file at 5 KB or less. If the raw log is larger,
  crop it to the relevant command, warning, error, exception, and nearby context.
  Do not include repetitive progress output such as download progress lines.

Cleanup:

- Do not start cleanup until the finalization gate passes or the user explicitly
  approves early closure with gaps.
- Copy the report/evidence out of the disposable install directory first.
- Stop the installed stack from the compose-file directory.
- Verify MTL Explorer quick-install containers are no longer running.
- Remove the disposable install directory.
- Do not globally prune Docker or remove unrelated containers, images, volumes,
  or directories.
- If cleanup fails, mark cleanup `FAIL` with the exact command and error.

Final response: summarize pass/fail, highest-priority failures, cleanup status,
and the report path.
````
