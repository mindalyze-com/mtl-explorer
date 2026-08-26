# Full Regression Packet Template

Copy this template to
`documentation/testing/full-regression/test_runs/<YYYY-MM-DD_HHMM-short-slug>/packets/<coverage-id>.md`
for each coverage packet. Use the same shape for `RUN_SETUP.md` and
`RUN_CLEANUP.md`.

```markdown
# Packet: <coverage-id-or-run-packet>

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
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
| TRD_01 | <what was done> | <expected UI/system result> | <observed result> | PASS/PARTIAL/FAIL/BLOCKED/NOT COVERED/NOT APPLICABLE/FIXED/REJECTED/NOT REPRODUCEABLE/NOT REPRODUCIBLE | [assets/<coverage-id>-<short-name>.webp](../assets/<coverage-id>-<short-name>.webp); [assets/<coverage-id>-<short-name>.txt](../assets/<coverage-id>-<short-name>.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/<coverage-id>-<short-name>.webp](../assets/<coverage-id>-<short-name>.webp) | <short caption> |
| [assets/<coverage-id>-<short-name>.txt](../assets/<coverage-id>-<short-name>.txt) | <short command/log summary> |

## Screenshot Evidence

![<short caption>](../assets/<coverage-id>-<short-name>.webp)

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
- New findings start as `OPEN`. Use `FIX_IN_WORK` only after an owner starts
  implementing, reviewing, or directly verifying a fix; regression investigation
  or triage alone is not fix work.
- `FIXED` requires direct retest evidence that a previously recorded issue now
  behaves as expected.
- `REJECTED` requires a concrete reason that the candidate issue is expected
  behavior, invalid, or outside the coverage requirement.
- `NOT REPRODUCEABLE` or `NOT REPRODUCIBLE` requires direct evidence that the
  recorded reproduction path was attempted and did not reproduce the issue.
  Prefer `NOT REPRODUCIBLE` for new entries; the gate accepts both spellings.
- `PARTIAL` must name the missing child checks and keep the coverage ID
  resumable in `run-state.md`.
- `NOT COVERED` means the coverage ID still lacks direct execution evidence; it
  is resumable during a normal full regression, not a terminal packet result.
- `BLOCKED` must explain the dependency, permission, service, data, or tooling
  constraint and whether it blocks the full regression.
- `NOT APPLICABLE` must explain why the coverage text does not apply to this
  configured run.
- Evidence should be compact: WebP screenshots where useful, each WebP
  screenshot asset at 85 KB (85,000 bytes) or less, `.txt` log snippets under
  5 KB, and no bulky traces unless needed for a failure.
- Every packet evidence asset reference must be a Markdown link. From packet
  files, use `../assets/<filename>` as the href while keeping the visible label
  as `assets/<filename>`.
- Embed packet WebP screenshots inline with Markdown image syntax in `Screenshot
  Evidence`; link text/log evidence instead of pasting long content.
- If the packet mutates shared state, record the before and after state in the
  packet and in `run-state.md`.
