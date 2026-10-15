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
[full regression start instructions](full-regression/start.md).

Run the focused frontend regression with:

```bash
cd mtl-client
npm run test:media-regression
```

For thumbnail recovery, cover a burst of 120 uncached photos with a busy converter,
opening despite a failed preview, retry on activation, keyboard and touch activation,
and cancellation when leaving the collection or hiding thumbnails. Verify that a
cached preview loads during thumbnail conversion, an uncached preview survives
60 seconds of converter contention, and queued thumbnails wait across preview retries. Check activity photos, the media
mosaic, and the filmstrip in light and dark themes. A filmstrip drag must not
restart recovery or select a photo.
