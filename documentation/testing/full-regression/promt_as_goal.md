# Prompt As Goal

## Context

ChatGPT 5.5 xhigh and Claude 4.7 Opus both struggle with this job because it is too long. They usually stop somewhere in the middle or pick what they consider most important. That is understandable, but still annoying.

## Better Approach (it seems)

A better approach would be to split the work properly, use a Clawbot-like loop, or maybe another tool.

No time right now. Codex has a new "Goal" option, and prompting it as a goal seems to work better for longer runs.

## Goal Prompt

```text
This is a long task. Split it into doable pieces.

Use documentation/testing/full-regression/retest-instructions.md as the base and work through it. The file references the frontend-regression-plan, which has dozens of tasks. Keep picking one task after another, test it, and document the results as you go.

Use the following server: YYYYY.
Root login password: XXXX.

Your job is finished once you are through everything. Produce one final report that collects all results.
```
