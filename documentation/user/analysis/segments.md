# Find repeated segments

Find every time you rode or walked the same stretch, and compare your attempts.

1. Open **Segments**. It uses the tracks in your current
   [filter](../filters/selection.md).
2. Place zones on the map in order (for example start and finish) and adjust
   their size.
3. Check how many tracks pass through, then start the analysis.
4. Choose the segment and what to show: time, speed, or distance.
5. Expand a row to see each attempt.
6. Select attempts, then choose **Compare** or **Replay**.

A track must pass through every zone to count. Adding a zone means fewer
matches. One activity can pass the same zones several times.

**Edit zones** lets you change the zones; this starts a new analysis.
**Results** goes back to the current results. After a file changes,
**Refresh results** updates the attempts.

## Time and gaps

Stops are included in the time. Breaks are pauses of at least one minute.

Gaps or missing timestamps can make time, speed, or replay unavailable for an
attempt. Attempts with gaps are left out of averages and trends but can still be
opened. Stop values with partial data are marked **Observed**.

See [segment analysis](../../architecture/analysis/segments.md) for the exact
matching rules.
