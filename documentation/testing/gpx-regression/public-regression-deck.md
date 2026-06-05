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
- Intentional multi-day files split into a small number of substantial tracks,
  not hundreds of one-point fragments.
