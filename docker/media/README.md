# MTL Explorer media packages and tests

The app installs precompiled conda-forge packages for Linux AMD64 and ARM64.
Stable upstream versions checked on 2026-09-04:

| Tool | Version | Package variant |
| --- | --- | --- |
| [libvips](https://github.com/libvips/libvips/releases/tag/v8.18.6) | 8.18.6 | Native image previews |
| [ImageMagick](https://imagemagick.org/mirror/) | 7.1.2-31 | `imagemagick_*` |
| [FFmpeg](https://ffmpeg.org/download.html) | 9.0.1 | `gpl_*`, including x264/x265 |
| [libheif](https://github.com/strukturag/libheif/releases/tag/v1.23.3) | 1.23.3 | `gpl_*`, including HEIC encoding |

`media-specs.txt` selects these versions. `conda-linux-64.lock` and
`conda-linux-aarch64.lock` pin the complete dependency sets with SHA-256 hashes.
The installer also checks the standalone micromamba 2.9.0-0 binary's hash.
It performs no dependency solving or media source compilation during a build.

The complete environment stays at `/opt/mtl/media` in both build and runtime
stages so library paths and plugins resolve. Its `bin` directory is on `PATH`;
the app's Python environment stays separate. Package metadata, license notices
and the installed lock are retained under `conda-meta` and `share/mtl`.
Conda-forge includes more codecs and supporting libraries than the former
custom build; the locks contain 206 packages for AMD64 and 196 for ARM64.

## Preview processing and fallback

`MediaController` calls `ImagePreviewService` for resized images and HEIC-to-JPEG
responses. The service runs `mtl-vips-preview` (installed from `vips-preview.py`)
first. This adapter calls libvips through its C ABI, uses one worker, disables
the operation cache, preserves aspect ratio and orientation, and never enlarges
an image. JPEG quality is 92; PNG previews retain transparency. It writes to
stdout in the same process that the server limits and terminates on timeout.

Only exit code 64 (no native format loader) invokes ImageMagick. The adapter
blocks libvips' optional ImageMagick bridge, so fallback cannot happen silently.
Decoder errors, incomplete output, missing executables and timeouts remain
errors. They do not trigger another expensive conversion attempt.

Each fallback attempt produces a `WARN` log and a `MediaPreviewFallbackEvent`
Spring event with the source, both processors and reason. Successful generated
responses include `X-MTL-Image-Processor: libvips` or `imagemagick`; fallback
responses also include `X-MTL-Image-Fallback: unsupported-format`. The event
is emitted even if ImageMagick subsequently fails. These are server and HTTP
signals; no UI notification is added.
Immediately before launching ImageMagick, a final conversion `WARN` records
the file, requested size and reason, and warns that fallback may use more RAM
than libvips. Other server threads can still write subsequent log lines.

Both engines retain the shared process limit, 30-second timeout, temporary-file
output validation and no-store error responses. Generated-image ETags change
with this renderer update so earlier previews are regenerated.

## Updating packages

Check upstream stable releases, edit `media-specs.txt`, then run with
[micromamba](https://mamba.readthedocs.io/en/latest/installation/micromamba-installation.html)
available:

```bash
python3 docker/media/update-locks.py
```

The command resolves both platforms from conda-forge only. Review both lock
diffs and run the runtime checks below before releasing. The installer rejects
locks generated from a different `media-specs.txt`.

## Runtime checks

`test_heic_regression.py` runs the installed libvips adapter against a real
iOS 18 HEIC. It uses the server's command, JPEG quality and 30-second
timeout. At 192, 2048 and 4096 pixels it requires a successful exit, complete
JPEG framing, independently decoded pixels, the expected portrait dimensions
and nonblank output. It makes no network requests and does not mock the decoder.
It requires native libvips success: ImageMagick cannot hide a HEIC regression.
The Docker build performs no ImageMagick HEIC conversion or encoding test.

`test_vips_preview.py` checks orientation, original size, no enlargement, PNG
transparency, a damaged HEIC and missing input. A synthetic BMP requires the
explicit unsupported-format exit and a successful ImageMagick conversion.

`test_media_tools.py` checks the packaged libheif version and uses a synthetic
HEVC video with audio to verify JPEG thumbnails, H.264/AAC fMP4 HLS output,
FFprobe metadata and decoding the completed stream.

The app Dockerfile runs these tests in the runtime stage for each target
architecture. BuildKit mounts the script and fixture only for the test; they
are not installed in the runtime image. Keep this check when changing media
packages. The server tests separately cover HTTP error handling and caching.

To build and check only the media runtime, without Java or GPSBabel compilation:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  --target media-runtime --output type=cacheonly .
```

To check an existing local image from the repository root:

```bash
docker run --rm --entrypoint python3 \
  --mount type=bind,source="$PWD/docker/media",target=/tests,readonly \
  mytraillog:local -m unittest discover -s /tests -p 'test_*.py' -v
```

For the HEIC test alone, use `/tests/test_heic_regression.py` as the Python
argument. Add `--cpus 1 --memory 256m --memory-swap 256m` before the image name
to exercise the constrained preview budget. Python and Pillow are required;
both are in the app image.

## Public fixture

- File: `fixtures/iphone-ios18-auxiliary.heic` (3,946,347 bytes).
- Original: `IMG_0307.HEIC`, iPhone 16 Pro Max / iOS 18.2; a pen on a countertop.
- Author: GitHub user `rhatguy`.
- Source: [PiGallery2 issue 965](https://github.com/bpatrik/pigallery2/issues/965),
  [attachment comment](https://github.com/bpatrik/pigallery2/issues/965#issuecomment-3267208268),
  [original ZIP](https://github.com/user-attachments/files/22215618/IMG_0307.HEIC.zip).
- Test-use permission from the author's issue: “This image is free to use as a
  test case if desired.”
- HEIC SHA-256: `845daac2606330ec418f02713f3fbd563a9ea9216b00af21265c11fc79411699`.
- ZIP SHA-256: `cfcf1b8441c9d8a6518747335308ff47f0384c918e684fc8bddc75d7a9511f9e`.

The HEIC is unmodified. Do not resize, strip metadata or re-encode the fixture:
its auxiliary image references trigger the original decoder failure. The test
checks its checksum to preserve that property.

Verified locally on Linux ARM64 on 2026-09-04:

- ImageMagick 6.9.11-60 / libheif 1.12.0: conversion failed at all three sizes.
  Reading the fixture directly through libheif reported
  `Too many auxiliary image references`.
- ImageMagick 7.1.2-30 / libheif 1.23.3: all three sizes passed in 8.5 seconds
  total, producing JPEGs at 144×192, 1536×2048 and 3072×4096 pixels.

The initial conda-forge ImageMagick HDRI build passed in isolation but exceeded
30 seconds during concurrent alpha builds. Its larger pixel buffers spilled
to disk under the 64 MiB memory / 128 MiB map limits. Raising those limits did
not fit the app's memory budget.

With libvips 8.18.6, both `media-runtime` builds passed all eight runtime tests
on 2026-09-04 (AMD64 through OrbStack/Rosetta). The 4096-pixel HEIC conversion
took 0.43 seconds on ARM64 and 0.85 seconds on AMD64 in these builds. Timings
depend on host load and CPU limits. The public HEIC remains unchanged.
All eight tests also passed in a standalone ARM64 runtime limited to one CPU
and 256 MiB RAM with no swap; the 4096-pixel HEIC preview took 1.08 seconds.

Backend checks run in the development container: `ImagePreviewServiceTest`,
`MediaControllerContentTest`, `MediaControllerPositionTest`,
`MediaControllerTrendTest`, `GeneratedImageOutputTest` and
`MediaProcessLimiterTest`. They cover fallback routing/events, response headers,
failure caching, converter cleanup and the shared process limit.
