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

You are allowed to install missing Docker prerequisites before running the
documented container-build flow. Treat that setup as work the documentation
normally expects from the user, and report it separately from the MTL Explorer
flow result.

Act as a strict tester:

- Record the exact GitHub commit, server OS, RAM/disk baseline, Docker/Buildx/
  Compose versions, commands executed, and command results.
- If Docker Engine, Buildx, or the Docker Compose plugin is missing, install
  only the missing Docker prerequisite, record the exact versions, and keep that
  setup work separate from the documented MTL Explorer flow.
- If a documented step fails, capture the exact command, error, likely cause,
  and whether the end-to-end flow is blocked.
- If the stack starts, keep verification technical. Verify the documented app
  URL, login only if needed to inspect the running GUI, and do not run the broad
  quick-install UI functional pass here.
- Download a handful of public internet GPX samples with trackpoint timestamps,
  place them in the GPX folder named by `documentation/container-build.md`, and
  let MTL Explorer sync them. Use at least three GPX files unless a source is
  unavailable; record each source URL, destination filename, checksum, file
  size, trackpoint count, and timestamp count.
- After the imported GPX files sync, delete one GPX file from the documented GPX
  folder, wait for MTL Explorer to process the deletion, and verify in the GUI
  that the deleted track is no longer counted or visible.
- Do not capture screenshots for container-build retests. Use concise command
  output, endpoint checks, container status, logs, and text observations as the
  evidence.
- Report format:
  - The primary report must be Markdown.
  - If a downloadable report is requested, also generate a PDF from the same
    report content.
  - The very first line must be a clear, visually prominent result summary:
    `> **RESULT: PASS - <one concise reason>**` or
    `> **RESULT: FAIL - <one concise reason>**`.
  - After that first line, include the detailed report with goal, environment,
    exact steps, evidence, issues, and conclusion.
  - Keep the summary concise; put detailed command output in the evidence
    sections.
  - Keep the report package small: target 500 KB or less for `report.md` plus
    assets, with 1 MB as the maximum unless there is a clearly stated reason.
  - Do not save endless logs. Crop command output to the relevant command,
    version, success, failure, and error lines; use excerpts instead of raw full
    logs when output is long.
- Save a concise Markdown report at:
  `documentation/testing/container-build/test_runs/<YYYY-MM-DD-short-slug>/report.md`
- Save any small technical report assets under that same run directory, for
  example:
  `documentation/testing/container-build/test_runs/<YYYY-MM-DD-short-slug>/assets/`

The final answer should summarize whether the documented container-build flow
passed end to end, what failed if anything, and where the report was written.
```
