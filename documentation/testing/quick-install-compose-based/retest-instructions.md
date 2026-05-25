# Quick Install Compose Retest Prompt

Use this prompt when asking an agent to retest the MTL Explorer quick install
from the root README. Replace the placeholders before sending.

````text
Please test the MTL Explorer quick install on this server:

- IP: <server-ipv4>
- SSH user: <ssh-user>
- SSH password/key note: <temporary-credential-or-access-note>

Use GitHub `main` as the source:
https://github.com/mindalyze-com/mtl-explorer

Treat the root `README.md` in that GitHub checkout as the single source of
truth. Relative path after checkout: `README.md`.
Direct GitHub path:
https://github.com/mindalyze-com/mtl-explorer/blob/main/README.md

Read the `Quick start` section on the checked-out `main` branch and follow it
step by step. Do not duplicate, reinterpret, or replace its install steps from
memory.

You are allowed to install missing Docker prerequisites before running the
README Quick start. Treat that setup as work the README normally expects from
the user, and report it separately from the MTL Explorer quick-install result.

The quick install flow to verify is the README's compose-based flow:

```bash
mkdir mtl-explorer
cd mtl-explorer
curl -fsSL -o docker-compose.yml https://raw.githubusercontent.com/mindalyze-com/mtl-explorer/main/docker-compose.yml
docker compose up -d
```

Act as a strict tester:

- Record the exact GitHub commit, server OS, RAM/disk baseline, Docker Engine
  version, Docker Compose plugin version, commands executed, and command
  results.
- Verify the README prerequisite first: Docker Engine and the Docker Compose
  plugin must be installed and support `docker compose`.
- If Docker Engine or the Docker Compose plugin is missing, install only the
  missing Docker prerequisite, record the exact versions, and keep that setup
  work separate from the MTL Explorer quick-install result.
- Run the README quick-start commands in a fresh test directory.
- If a documented step fails, capture the exact command, error, likely cause,
  and whether the end-to-end flow is blocked. Do not apply product workarounds.
- If the stack starts, verify `http://localhost:18080/mtl/`, then use the
  Browser tool to run the quick-install functional test. This is not just a
  smoke test: cover the main functions and capture compact WebP screenshots for
  the important states.
- Download a handful of public internet GPX samples with trackpoint timestamps,
  place them in the GPX folder named by the README quick start (`./data/gpx/`),
  and let MTL Explorer sync them. Use at least three GPX files unless a source
  is unavailable; record each source URL, destination filename, checksum, file
  size, trackpoint count, and timestamp count.
- After the imported GPX files sync, delete one GPX file from `./data/gpx/`,
  wait for MTL Explorer to process the deletion, and verify in the GUI that the
  deleted track is no longer counted or visible.
- Cover these quick-test functions explicitly:
  login with `mtl` / `change-me`, map render, adding GPX files through
  `./data/gpx/`, imported track visibility, GPX deletion reflection in the GUI,
  location search, drawing/route editing controls, statistics for imported
  tracks, saving a planned/drawn track, and planner routing with computed
  distance/elevation/duration.
- For every main function, record the exact UI action, expected result, actual
  result, and pass/fail status. If a function cannot be completed, capture the
  exact failing step, UI state, likely cause, and whether it blocks the
  quick-install result.
- Report format:
  - The primary report must be Markdown.
  - If a downloadable report is requested, also generate a PDF from the same
    report content.
  - The very first line must be a clear, visually prominent result summary:
    `> **RESULT: PASS - <one concise reason>**` or
    `> **RESULT: FAIL - <one concise reason>**`.
  - After that first line, include the detailed report with goal, environment,
    exact steps, evidence, screenshots/assets, issues, and conclusion.
  - Keep the summary concise; put detailed command output and screenshots in the
    evidence sections.
  - Keep the report package small: target 500 KB or less for `report.md` plus
    assets, with 1 MB as the maximum unless there is a clearly stated reason.
  - Do not save endless logs. Crop command output to the relevant command,
    version, success, failure, and error lines; use excerpts instead of raw full
    logs when output is long.
  - Capture only the important screenshots. Prefer a small pass set, and on
    failure focus screenshots on the failing page/state and the immediate
    evidence needed to understand it.
  - Keep screenshots/assets compact. Crop to the relevant UI, avoid full-page
    screenshots unless needed, and save screenshot assets as WebP. If the
    screenshot tool emits PNG, convert the final report assets to WebP and keep
    PNG only when WebP conversion is unavailable or hurts legibility.
- Save a concise Markdown report at:
  `documentation/testing/quick-install-compose-based/test_runs/<YYYY-MM-DD-short-slug>/report.md`
- Save screenshots or other small report assets under that same run directory,
  for example:
  `documentation/testing/quick-install-compose-based/test_runs/<YYYY-MM-DD-short-slug>/assets/`

The final answer should summarize whether the README quick install passed end
to end, what failed if anything, and where the report was written.
````
