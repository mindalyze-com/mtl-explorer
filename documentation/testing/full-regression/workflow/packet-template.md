# Full Regression Packet Template

Copy this template to
`documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/packets/<coverage-id>.md`
for each coverage packet. Use the same shape for `RUN_SETUP.md` and
`RUN_CLEANUP.md`.

```markdown
# Packet: <coverage-id-or-run-packet>

## Scope

- Coverage source:
- Coverage ID or run packet:
- In scope:
- Out of scope:

## Prerequisites

- Required previous coverage IDs or run packets:
- Required app/data state:
- Required browser context:

## Allowed Mutations

- Allowed:
- Not allowed:

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | <what was done> | <expected UI/system result> | <observed result> | PASS/PARTIAL/FAIL/BLOCKED/NOT COVERED/NOT APPLICABLE | <assets/... or note> |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/<coverage-id>-<short-name>.webp | <short caption> |
| assets/<coverage-id>-<short-name>.txt | <short command/log summary> |

## Timings

| Step | Timing |
|---|---:|

## Handoff Notes

- Completed:
- Remaining unfinished coverage:
- Blocked or not applicable:
- State left for the next packet:
```

## Completion Rules

- Each packet covers one coverage ID and must have an explicit status.
- `PASS` requires direct action and evidence, not a broad prefix summary.
- `PARTIAL` must name the missing child checks and keep the coverage ID
  resumable in `run-state.md`.
- `NOT COVERED` means the coverage ID still lacks direct execution evidence; it
  is resumable during a normal full regression, not a terminal packet result.
- `BLOCKED` must explain the dependency, permission, service, data, or tooling
  constraint and whether it blocks the full regression.
- `NOT APPLICABLE` must explain why the coverage text does not apply to this
  configured run.
- Evidence should be compact: WebP screenshots where useful, `.txt` log snippets
  under 5 KB, and no bulky traces unless needed for a failure.
- If the packet mutates shared state, record the before and after state in the
  packet and in `run-state.md`.
