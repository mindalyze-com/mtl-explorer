# Full Regression Quick-Install Prompt

Use this prompt when asking an agent to install MTL Explorer from the README,
run the full end-user regression with the resumable packet workflow, write a
report, and clean up the test server. Replace the placeholders before sending.

````text
Please test the MTL Explorer quick install plus full end-user regression on:

- IP: <server-ipv4>
- SSH user: root
- SSH password/key note: <temporary-credential-or-access-note>
- App image: <app-image>
- Compose image override: `MTL_APP_IMAGE`

Use GitHub `main` from https://github.com/mindalyze-com/mtl-explorer for the
quick-install README and Compose source.

Read the quick-install facts from that GitHub source before acting:

- `README.md`

Read these workflow files from the current workspace before acting:

- `documentation/testing/frontend-regression-test-plan.md`
- `documentation/testing/full-regression/workflow/resumable-workflow.md`
- `documentation/testing/full-regression/workflow/packet-template.md`
- `documentation/testing/full-regression/workflow/init-run.py`
- `docker/gpx_porto_taxi_dataset/generate_regression_photos.py`
- `docker/gpx_porto_taxi_dataset/photo_placeholder.py`

Derive all quick-install commands, app URLs, credentials, prerequisites, and
import-folder paths from the README. Derive regression coverage and packet order
from the coverage IDs in the test plan. Use one packet per coverage ID. Use the
workflow files for state tracking, packet result format, and final report
assembly. Freeze the current workspace test plan when initializing the run and
use that snapshot for the complete run. Do not use memory or invented defaults;
report missing required details as documentation gaps.

Execution guidance:

- This is a long, evidence-heavy task. Do not run it as one unstructured pass.
  Before testing, run `workflow/init-run.py` exactly once with the target server,
  SSH user, app image, and a short slug. The initializer creates the run folder,
  `run-state.md`, `coverage-plan.md`, `packets/`, and `assets/`. Use the printed
  run folder and work through its frozen coverage IDs from top to bottom.

  ```bash
  documentation/testing/full-regression/workflow/init-run.py \
    --server <server-ipv4> \
    --ssh-user root \
    --app-image <app-image> \
    --slug <short-slug>
  ```
- The lead/coordinator owns `run-state.md` and final `report.md`. Packet workers
  may only write their own `packets/<coverage-id>.md` file and
  `assets/<coverage-id>-*` files.
- If sub-agents or delegated work are available, delegate only one coverage ID
  per packet, with explicit prerequisites and handoff notes. The lead remains
  responsible for the final coverage matrix, statuses, and conclusion.
- Shared-state mutations must be serialized: `RUN_SETUP` first, import IDs
  before UI checks, each delete flow after the checks that need its full
  dataset, and `RUN_CLEANUP` last. In particular, run media setup `MED_06`
  before `MED_01`, and media deletion `MED_07-MED_12` after `MED_40`. Read-only
  UI packets may run in separate browser contexts after import is complete.
- If the run pauses or an agent stops, resume from `run-state.md`, its sibling
  `coverage-plan.md`, and the packet files. Do not rely on conversation memory
  or replace the snapshot with the current workspace plan.
- On resume, continue with the first `IN PROGRESS`, `NOT STARTED`, `PARTIAL`, or
  `NOT COVERED` coverage ID in queue order. `PARTIAL` and `NOT COVERED` are
  unfinished handoff states during an active run, not reasons to skip ahead.

Install and test:

- Work over SSH in a fresh disposable directory on the target server.
- Follow the README quick-install steps exactly, except for the disposable
  parent directory needed to isolate the run and the requested app-image
  override.
- Before the first Compose start, set `MTL_APP_IMAGE=<app-image>` in the
  disposable Compose installation. Use the same override for every later
  Compose operation in the run.
- Verify the effective image with Compose configuration and the running app
  container. Record the requested image reference, resolved image ID or digest,
  and reported MTL Explorer build/version in `RUN_SETUP` evidence.
- Install only missing Docker prerequisites if needed, and report that setup
  separately from the MTL Explorer result.
- Treat this as a black-box installed-app regression. Do not inspect or change
  product source code, run source builds/tests, start dev servers, or apply
  product workarounds.
- Verify the documented local app URL from the server and the browser-accessible
  remote URL derived from it.
- Use only README-documented login credentials.

Run the full user-facing regression plan:

- Use the run's frozen `coverage-plan.md` coverage IDs as the coverage matrix.
- Create one packet file per coverage ID, for example `packets/TRD_01.md`.
  Every packet result must follow `workflow/packet-template.md`.
- Treat every coverage ID in the run's frozen `coverage-plan.md` as required
  unless it is explicitly not applicable to the run. Do not collapse an ID
  prefix or chapter into one passing row unless all child IDs were actually
  exercised.
- For every user-facing coverage ID, record action, expected result, actual
  result, status, and evidence.
- A packet is terminal only when its coverage ID has direct evidence for a
  terminal status: `PASS`, `FAIL`, `BLOCKED`, `NOT APPLICABLE`, `FIXED`,
  `REJECTED`, `NOT REPRODUCEABLE`, or `NOT REPRODUCIBLE`. `PARTIAL` and
  `NOT COVERED` must remain resumable unless the user explicitly approves
  closing the run with gaps.
- Test desktop and narrow mobile/touch viewports. Include hard refresh, normal
  reload, back/forward navigation, a clean browser context where useful,
  console errors/warnings, and failed network requests.
- For `MAP_13-MAP_15`, run a short isolated map-provider pass in addition to the
  normal map checks. For quick-install compose targets, create a temporary
  compose override that sets the app container environment
  `MTL_MAP_SERVER_TILE_MODE=remote`, restart only the app service, then verify
  the configured remote raster providers, attribution, and absence of
  `/api/map-proxy` tile requests. For fallback coverage, simulate local vector
  tile unavailability only when the environment exposes a safe control, such as
  stopping the local `map-server` sidecar in a `local-maps` profile run or
  blocking local PMTiles requests; otherwise mark `MAP_14` `BLOCKED` with the
  missing control. For `MAP_15`, restore local-vector mode, use the in-app Map
  Source control to select Remote, and confirm the persisted source override
  uses remote raster tiles without proxy requests. Restore the original
  deployment configuration before continuing the remaining coverage.
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
- Generate the standard disposable media/activity set with the renderer already
  packaged in the app image:

  ```bash
  docker compose exec -T app python3 \
    /app/demo/generate_regression_photos.py \
    /app/logs/<run-id>-media-fixtures
  mkdir -p data/gpx/<run-id> data/media/<run-id>
  cp data/logs/<run-id>-media-fixtures/mtl-regression-media-track.gpx data/gpx/<run-id>/
  cp data/logs/<run-id>-media-fixtures/*.jpg data/media/<run-id>/
  cp data/logs/<run-id>-media-fixtures/*.mp4 data/media/<run-id>/
  cp data/logs/<run-id>-media-fixtures/*.mov data/media/<run-id>/
  ```

  Preserve `data/logs/<run-id>-media-fixtures/manifest.json` outside the watched
  tree. The generator uses the shared photo renderer and ffmpeg. It creates four
  GPS JPEGs, two camera-time-only JPEGs, an embedded-GPS MP4, a
  camera-time-only MOV, and their matching six-point GPX activity. Index the
  GPX first, then use Admin **Rescan Media** to index all eight media files. Use
  the complete set for `MED_01-MED_03`, `MED_05`, `MED_06`, `MED_27-MED_32`,
  and `MED_36-MED_40` before permanent deletion. Exercise `MED_04` separately
  with a safe HEIC fixture; use the dedicated 100,000-row setup for `MED_21`,
  `MED_28`, and `MED_33`.
- Delete the two synthetic files named for deletion, use Admin **Rescan Media**,
  and complete `MED_07-MED_12`. Verify the pins remain absent after freshness
  reload, viewport changes, a hard reload, and the three-minute media cache
  expiry. Confirm the active rows are gone while `indexed_file` removal state
  and `media_file_audit` delete snapshots remain. Never use private media for
  this flow.

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
  environment, evidence, finding status, and release impact. New findings start
  as `OPEN`. Use `FIX_IN_WORK` only when an owner has started implementing,
  reviewing, or directly verifying a fix; regression investigation or triage
  alone does not qualify.
- Record timings for Docker setup, quick install, container startup, track and
  media import sync, track and media deletion sync, desktop regression, mobile
  regression, offline/cache, final verification, and cleanup.

Report and evidence:

- Write a standalone Markdown report, not a transcript.
- Assemble the final report only from completed packet files and linked assets.
  Do not invent final statuses from memory or broad impressions.
- Before writing `report.md`, setting `Current coverage ID: COMPLETE`, or running
  `RUN_CLEANUP`, enforce the finalization gate from
  `workflow/resumable-workflow.md`: every coverage ID must be terminal
  (`PASS`, `FAIL`, `BLOCKED`, `NOT APPLICABLE`, `FIXED`, `REJECTED`,
  `NOT REPRODUCEABLE`, or `NOT REPRODUCIBLE`) and no packet/run-state row may
  remain `NOT STARTED`, `IN PROGRESS`, `PARTIAL`, or `NOT COVERED`.
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
- Save the synthetic media manifest as `assets/DAT_08-media-manifest.json` and
  link it from the media packets. Do not copy the generated media fixtures into
  the repository or report artifacts.
- Keep each WebP screenshot asset at 85 KB (85,000 bytes) or less. Crop or
  recompress screenshots that exceed the limit before finalizing the report.
- In packet Markdown files, make every evidence asset reference clickable. Since
  packet files live under `packets/`, link assets as
  `[assets/<filename>](../assets/<filename>)`.
- Embed packet WebP screenshots inline with Markdown image syntax, for example
  `![Short caption](../assets/<coverage-id>-<short-name>.webp)`, so packet files
  are readable without opening assets separately.
- Keep compact screenshots for working functions as well as failures, so the
  report gives a useful visual overview of validated areas such as login, map,
  imports, browser, stats, filters, details, admin, planner, mobile, and
  track/media deletion sync.
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
