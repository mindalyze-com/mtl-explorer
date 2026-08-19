# Photo handling improvement verification

Focused local verification for the Track Details photo timeline, server-side
activity matching, camera offsets, mini-map positions, and enhanced media
viewer.

Use only public tracks and fully synthetic media. Do not use local personal GPX
tracks or photos as fixtures or evidence.

## Reusable regression data

Run `docker/gpx_porto_taxi_dataset/generate_regression_photos.py <output-dir>`
in the app container or a Python environment with Pillow, piexif, ffmpeg, and
ffprobe. It creates six small synthetic JPEGs, a short H.264/AAC MP4, a short
H.264/AAC MOV, a manifest, and `mtl-regression-media-track.gpx`. Four photos and
the MP4 contain embedded GPS. Two photos and the MOV contain camera time only
and should resolve to the matching GPX route. The GPS points are near, but not
identical to, the track points.

For the full installed-app flow, follow the copy/index order in
[`full-regression/retest-instructions.md`](../full-regression/retest-instructions.md).

Run the focused frontend regression with:

```bash
cd mtl-client
npm run test:media-regression
```

- [2026-08-17 local end-to-end run](test_runs/2026-08-17-local-e2e/report.md)
- [2026-08-17 100k pagination follow-up](test_runs/2026-08-17-pagination-follow-up/report.md)
- [2026-08-17 Photo tools UX and 100k regression](test_runs/2026-08-17-photo-tools-ux/report.md)
- [2026-08-17 review follow-up](test_runs/2026-08-17-review-follow-up/report.md)
- [2026-08-17 viewer and map navigation follow-up](test_runs/2026-08-17-viewer-navigation/report.md)
- [2026-08-17 statistics media trends follow-up](test_runs/2026-08-17-statistics-media-trends/report.md)
