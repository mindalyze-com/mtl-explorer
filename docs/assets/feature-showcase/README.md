# Segment screenshot sources

Refreshed on 2026-09-06 using the current MTL Explorer UI and an isolated local
backend containing only 15 public Porto demo tracks. No private GPX files,
media, or health records were mounted or used.

The captures replace `segment-zones`, `segment-results`, `segment-compare`,
`segment-replay`, and `mobile-segments` at the sizes used by the website. Other images
in this directory have separate sources; see the
[Google Health image notes](google-health-sources.md) for those examples.

## Data

The routes come from
`docker/gpx_porto_taxi_dataset/porto_taxi_service_gpx_extract.zip`.
See the [dataset notes](../../../docker/gpx_porto_taxi_dataset/DATASOURCE.md).
Track IDs: 00526, 00859, 00926, 00942, 02936, 03298, 04082, 05644, 06437,
06780, 07549, 08951, 10951, 11343, and 13938.

Source: L. Moreira-Matias, M. Ferreira, J. Mendes-Moreira, L. Lopes, and
J. Gama, [UCI, 2013](https://doi.org/10.24432/C55W25),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Demo dates are simulated; they are not the original trip dates.

Basemap: © [OpenStreetMap](https://www.openstreetmap.org/copyright).
Elevation: [Mapterhorn](https://mapterhorn.com/attribution/).

## Capture

- Use the current frontend and backend with a separate, empty database.
  Import only the selected files from the public archive.
- Choose OSM Topo Light, 2D, with GPS tracks visible. Hide media, track points,
  and the heatmap.
- On the A4 near Custóias and São Mamede de Infesta, place three zones from
  west to east with a 250 m detection radius. All 15 routes cross them.
- Analyze B → A. Select the four newest results for desktop comparison.
  Capture the result list, speed chart, and replay paused at 25 seconds.
- On mobile, select the three newest results and pause replay at 25 seconds.
- Desktop captures are direct UI screenshots at 1280 × 720. The mobile
  source is 390 × 844. At the user’s request, the mobile image is presented
  in a generated phone mockup matching the original website image. The raw
  capture and edit prompt are kept in
  [the source notes](../../../documentation/tools/website/segment-preview/README.md).
- Encode as WebP at quality 88. Desktop widths: 360, 720, 1099, 1280.
  Mobile mockup widths: 360, 520, and 853 px.

The older public deployment was used only to identify the matching routes;
the refreshed screenshots show the local current UI.
