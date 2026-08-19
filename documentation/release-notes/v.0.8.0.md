# MTL Explorer Release v.0.8.0

This release expands media indexing, browsing, and video playback. It also
fixes stale media records after files are removed and rescanned.

## Media Browsing

- Added a Photos tab to Track Details with a paged timeline and mini-map.
- Added map collections for one location, a cluster, the current map view, and
  photos along a GPS track.
- Added photo and video trends to Statistics with period drill-downs.
- Expanded the media viewer with a filmstrip, metadata, nearby tracks, zoom,
  fullscreen controls, and a separate light or dark theme.

## Indexing And Positioning

- Improved media format detection and capture, GPS, image, and video metadata
  extraction.
- Added durable activity matching with preserved EXIF, estimated, and
  user-assigned position sources.
- Added reversible camera-clock correction and manual location assignment.
- Removed stale indexed and derived media records after source files are
  deleted and media is rescanned, addressing
  [#6](https://github.com/mindalyze-com/mtl-explorer/issues/6).

## Video Playback

- Added video thumbnails, byte-range streaming, and original-file downloads.
- Added optional on-demand H.264/AAC playback conversion when a browser cannot
  decode the original video.
- Added conversion progress, cancellation, reconnection, and bounded quality
  choices.

## Reliability And Performance

- Added bounded server-side paging for large media timelines and collections.
- Coordinated resource-intensive background jobs and limited concurrent image
  processing.
- Reduced memory use during demo media generation.
- Expanded automated and end-to-end coverage for indexing, media workflows,
  playback, and large collections.
