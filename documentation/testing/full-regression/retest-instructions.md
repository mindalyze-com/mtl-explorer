# Full Regression Quick-Install Prompt

Use this prompt when asking an agent to install MTL Explorer from the README,
run the full end-user regression, write a report, and clean up the test server.
Replace the placeholders before sending.

````text
Please test the MTL Explorer quick install plus full end-user regression on:

- IP: <server-ipv4>
- SSH user: root
- SSH password/key note: <temporary-credential-or-access-note>

Use GitHub `main` from https://github.com/mindalyze-com/mtl-explorer.

Read these source-of-truth files from that GitHub source before acting:

- `README.md`
- `documentation/testing/frontend-regression-test-plan.md`

Derive all quick-install commands, app URLs, credentials, prerequisites, and
import-folder paths from the README. Derive regression coverage from the test
plan. Do not use memory or invented defaults; report missing required details as
documentation gaps.

Execution guidance:

- This is a long, evidence-heavy task. If sub-agents or delegated work are
  available, split it into controlled work packets such as install/data
  preparation, desktop regression, mobile/PWA/offline regression, and
  evidence/report QA. The lead agent remains responsible for the final coverage
  matrix, statuses, and conclusion.
- Because these checks operate on the same installed app and imported dataset,
  sub-agents may need to run in coordinated phases instead of fully in
  parallel. Avoid concurrent actions that mutate shared data, browser session
  state, import folders, or cleanup state.

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

- Use the frontend regression test plan as the coverage matrix.
- Mark source/static/API-type rows `NOT APPLICABLE - black-box quick-install regression`.
- Treat every checklist bullet in the frontend regression test plan as a
  required coverage item unless it is explicitly not applicable to the run.
  Do not collapse a section into one passing row unless all child bullets were
  actually exercised.
- For every user-facing coverage item, record action, expected result, actual
  result, status, and evidence.
- Test desktop and narrow mobile/touch viewports. Include hard refresh, normal
  reload, back/forward navigation, a clean browser context where useful,
  console errors/warnings, and failed network requests.
- Run offline/cache coverage only in an installed PWA / installed web-app
  browser context after one successful online load. If the app is only opened as
  a normal browser tab, do not fail the row for offline reload behavior; mark the
  installed-PWA offline row `NOT APPLICABLE` or `NOT COVERED`, explain that
  offline mode requires browser installation, then restore connectivity and
  verify normal online recovery.

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
  service, or data source is unavailable. Use `BLOCKED` or `NOT COVERED`, explain
  why, and state whether it blocks the full regression.
- Do not pass a parent area when any child coverage item is skipped,
  spot-checked only, or verified indirectly. Use `PARTIAL`, `NOT COVERED`, or
  `NOT APPLICABLE` and name the missing child checks.
- Assign findings IDs and severities: `P0`, `P1`, `P2`, or `P3`.
- For each issue, include reproduction steps, expected/actual result,
  environment, evidence, and release impact.
- Record timings for Docker setup, quick install, container startup, import
  sync, deletion sync, desktop regression, mobile regression, offline/cache,
  final verification, and cleanup.

Report and evidence:

- Write a standalone Markdown report, not a transcript.
- First line must be:
  `> **RESULT: PASS - <one concise reason>**` or
  `> **RESULT: FAIL - <one concise reason>**`.
- Use `PASS` only if quick install succeeds, required regression coverage runs,
  cleanup succeeds, and no blocking/high-severity failures remain.
- Include goal, scope, environment, extracted README facts, setup/install result,
  timings, coverage matrix, issues, evidence, cleanup, blocked/untested areas,
  and conclusion.
- Save the report at
  `documentation/testing/full-regression/test_runs/<YYYY-MM-DD-short-slug>/report.md`.
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
