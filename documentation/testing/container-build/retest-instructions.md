# Container Build Retest Prompt

Use this prompt when asking an agent to retest the MTL Explorer container-build
flow. Replace the placeholders before sending.

```text
Please test the MTL Explorer container-build flow on this server:

- IP: <server-ipv4>
- SSH user: root
- SSH password/key note: <temporary-credential-or-access-note>

Use GitHub `main` as the source:
https://github.com/mindalyze-com/mtl-explorer

Treat `documentation/container-build.md` in that GitHub checkout as the single
source of truth for the procedure. Read it on the checked-out `main` branch and
follow it step by step. Do not duplicate, reinterpret, or replace its build
steps from memory.

Act as a strict tester:

- Record the exact GitHub commit, server OS, RAM/disk baseline, Docker/Buildx/
  Compose versions, commands executed, and command results.
- If prerequisites are missing, install only what is necessary and report that
  separately from the documented MTL Explorer flow.
- If a documented step fails, capture the exact command, error, likely cause,
  and whether the end-to-end flow is blocked.
- If the stack starts, use the Browser tool to verify the web app directly and
  capture screenshots for the important checks.
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
  `documentation/testing/container-build/test_runs/<YYYY-MM-DD-short-slug>/report.md`
- Save screenshots or other small report assets under that same run directory,
  for example:
  `documentation/testing/container-build/test_runs/<YYYY-MM-DD-short-slug>/assets/`

The final answer should summarize whether the documented container-build flow
passed end to end, what failed if anything, and where the report was written.
```
