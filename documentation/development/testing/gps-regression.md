# Public GPX Regression Deck

MTL Explorer keeps a small public GPX regression deck in
`mtl-server/src/test/resources/gpx/public-regression`.

The deck uses CC0 fixtures from https://www.viewmygpx.com/sample-gpx-files/.
They are synthetic, realistic GPX files with no real user data. Private tracks
must not be added to this deck.

The tests first audit the source GPX directly, then run the MTL Explorer import
path. This is intentional: a source problem should fail separately from a
processing regression.

Current invariants:

- Source point counts, track counts, and waypoint counts match the manifest.
- Source timestamps are monotonic inside each segment.
- Source adjacent distances stay within plausible bounds.
- Imported clean tracks preserve realistic length and point counts.
- Imported tracks do not contain impossible adjacent jumps.
- Short multi-point glitches are rejected at 10 Hz (60 m) and 5 Hz (100 m).
  Millisecond fix pairs recover when normal cadence resumes; a real observation
  gap during probation receives bounded time credit without accumulating cluster time.
- Intentional multi-day files split into a small number of substantial tracks,
  not hundreds of one-point fragments.
- Duplicate timestamps retain supported geometry and motion totals without a
  pending review task. A backward clock beyond one second requires review.
- An isolated backward alert preserves supported section timing. Systemic clocks
  remain contained after reassessment. Reimport rebuilds previously contained
  timing under current criteria without removing valid alerts.
- A supported isolated forward clock spike produces one incident and excludes
  only its canonical timestamp; RAW timestamps and supported intervals stay available.
- Routine denoising never creates aggregate distance, ascent or descent review.
  Stored aggregate-impact summaries resolve even after limited reassessment,
  preserving unrelated unchecked issues and acknowledgements; large
  per-point corrections and substantial rejection retain their review criteria.
- Review severity is based on section coverage and measured impact; ordinary
  pauses and isolated resolved corrections remain informational.
