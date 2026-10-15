# Google Health screenshot sources

Captured on 3 October 2026 from the running MTL Explorer application at
`http://localhost:18225/mtl/`, using only the Codex in-app browser. These replace
the retired standalone overview renderer and illustrative track frame.

## Data and screens

- `google-health-overview`: **Health → Overview**, maximized, All categories,
  Auto grouping, Last 30 days (4 September–3 October 2026). The visible charts
  show sleep and heart rate. The app's **Synthetic demo data** notice is retained.
- `google-health-track`: **Track details → Graphs** for **Synthetic demo:
  bicycle** (2 October 2026, local track ID 100198). Time axis, Range enabled,
  350 requested points, 160 px graph height, activity map collapsed. The track
  has 261 matching heart-rate samples. The synthetic label and empty elevation
  chart remain visible; the mathematical route has no recorded elevation.

The server's `googlehealth.demo` generator supplies both the mathematical route
and health records through the ordinary application APIs. They share one
fictional wearable timeline. No private GPX files, personal health records,
Google credentials, or account synchronization were used. The images do not
represent a recorded Garmin/Fitbit session.

## Preparation

Raw JPEG browser captures are kept at
`/Volumes/EXT_HD_1TB/mytraillog/website-health-refresh/2026-10-03/`.
The browser viewport was 1093 × 1045. Overview uses the whole viewport; the track
capture uses the top 1093 × 932 pixels, ending after the complete heart-rate chart.
No UI, chart values, or labels were redrawn or added.

Run `documentation/tools/website/prepare_health_screenshots.py` with that capture directory
to regenerate the WebP assets at quality 88. Resizing preserves aspect ratio
and never upscales:

| Variant | Overview | Track graphs |
| --- | --- | --- |
| thumb | 360 × 344 | 360 × 307 |
| medium | 720 × 688 | 720 × 614 |
| preview | 1093 × 1045 | 1093 × 932 |
| large | 1093 × 1045 | 1093 × 932 |

Homepage cards and the public Health guide use responsive variants. The
homepage lightbox and guide image links open the full capture. Repository
documentation uses the preview variant. Cache version: `20261003`.

These screenshots use the repository's AGPL-3.0 license. See the
[demo generator notes](../../../documentation/architecture/health/demo-data.md)
for generator configuration and limitations.
