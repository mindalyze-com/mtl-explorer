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
- If prerequisites are missing, report that clearly. If the request explicitly
  asks to assume prerequisites are present, install only the missing Docker
  prerequisite, record the exact versions, and keep that separate from the MTL
  Explorer quick-install result.
- Run the README quick-start commands in a fresh test directory.
- If a documented step fails, capture the exact command, error, likely cause,
  and whether the end-to-end flow is blocked. Do not apply product workarounds.
- If the stack starts, verify `http://localhost:18080/mtl/`, then use the
  Browser tool to test the main functions and capture screenshots:
  login with `mtl` / `change-me`, map render, GPX import via `./data/gpx/`,
  imported track visibility, location search, and planner routing.
- Use at least one public internet GPX sample with trackpoint timestamps for
  the visibility check. Record the GPX source URL and checksum.
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
    screenshots unless needed, and prefer compressed JPG/WebP over large PNGs
    unless PNG is necessary for legibility.
- Save a concise Markdown report at:
  `documentation/testing/quick-install-compose-based/test_runs/<YYYY-MM-DD-short-slug>/report.md`
- Save screenshots or other small report assets under that same run directory,
  for example:
  `documentation/testing/quick-install-compose-based/test_runs/<YYYY-MM-DD-short-slug>/assets/`

The final answer should summarize whether the README quick install passed end
to end, what failed if anything, and where the report was written.
````
