# Video transcoding verification

Use this focused regression to verify browser-compatible video fallback without
committing media fixtures.

Generate native fixtures with
`docker/gpx_porto_taxi_dataset/generate_regression_photos.py`. Generate an
archival-codec case in a temporary directory with ffmpeg, for example FFV1 or
ProRes video with PCM audio in a MOV container. Browser codec support varies.
If the test browser plays the archival codec, use a temporary local harness
that gives the production player a naturally unsupported source while its
transcode request points to the indexed synthetic video. Do not simulate the
media error in page code. Keep the harness and generated files out of Git and
remove them after the run.

Verify:

- native MP4 playback does not create a transcode session;
- decode/source errors offer Auto, 480p, 720p, and 1080p compatible playback;
- network errors retain the normal media retry path;
- progress, slow conversion, cancellation, retry, and original download remain
  usable;
- HLS playback reaches the full generated duration;
- reopening the same media revision and quality reuses prepared work;
- one active session, one completed result, byte/runtime limits, expiry,
  eviction, and startup cleanup bound temporary disk use;
- no generated video is added to Git.

Record automated commands, ffprobe output, browser evidence, temporary storage
measurements, session reuse, cleanup, and any findings under `test_runs/`.
