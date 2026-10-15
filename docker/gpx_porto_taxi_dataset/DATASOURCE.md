# Demo GPX Data — Source & Citation

These GPX files were derived from the **Porto Taxi Service Trajectory** public dataset,
part of the ECML/PKDD 2015 Taxi Trajectory Prediction Challenge.

The raw CSV taxi trajectories were converted to GPX format using the
`extract_tracks.py` script included in this repository.

## Dataset

- **Source:** UCI Machine Learning Repository
  https://archive.ics.uci.edu/dataset/339/taxi+service+trajectory+prediction+challenge+ecml+pkdd+2015
- **DOI:** https://doi.org/10.24432/C55W25
- **License:** Creative Commons Attribution 4.0 International (CC BY 4.0)

## Citation

L. Moreira-Matias, M. Ferreira, J. Mendes-Moreira, L. Lopes, and J. Gama.
"Taxi Service Trajectory - Prediction Challenge, ECML PKDD 2015,"
UCI Machine Learning Repository, 2013.
[Online]. Available: https://doi.org/10.24432/C55W25

## Notes

- Coordinates mostly cover **Porto, Portugal**; some trips extend beyond the city.
- Timestamps have been synthetically randomised to simulate realistic
  app-usage patterns; they do not reflect actual trip times.
- Fresh startup GPX files include a track description with the original Porto
  trip ID and explain the changed dates, added elevation, demo photos and
  synthetic heart rate data. The app imports this track-level description.

## Demo elevation

The original Porto taxi archive contains no recorded altitude. Fresh Docker
demo volumes add terrain-derived elevation profiles during GPX creation.
Existing GPX files are retained.

- Source: [Mapterhorn terrain tiles](https://mapterhorn.com/data-access/), with
  [source attribution](https://mapterhorn.com/attribution/). A source snapshot is
  bundled at `/app/demo/elevation/data/elevation-attribution.json`.
- Raster: zoom 9, 512-pixel tiles, roughly 115 m spacing near Porto; whole-metre
  heights. Coverage is the rectangle around all bundled track points expanded
  to four times its width and height, including trips outside the Porto map
  viewport. See [configuration](elevation/demo-elevation-config.json).
- Bilinear interpolation uses deterministic per-track random weights of
  80–120%, normalised to keep elevations within the surrounding sample range.
  These are synthetic demo heights; bridges, tunnels, and short climbs may differ.
- The checked-in raster stores heights in lossless WebP tiles (17.07 MB for the full margin).
  GPX creation expands tiles on demand with an 8 MiB cache limit. Startup needs
  no terrain download.
- Interrupted creation resumes completed files. The generated photo catalog
  retains original track positions and capture times.

Docker builds copy the prepared raster, checksum summary, and attribution from
`elevation/data/`; they do not download or compress terrain. The preparation tools
and configuration remain in `elevation/` for on-demand regeneration.

From the repository root, with the client npm dependencies installed and Pillow
available to Python, regenerate on the mounted external drive, then copy the
three prepared files into the repository:

```sh
test -d /Volumes/EXT_HD_1TB && mount | grep -q ' on /Volumes/EXT_HD_1TB ' || exit 1
DEMO_ELEVATION_WORK=/Volumes/EXT_HD_1TB/mytraillog/demo-elevation
mkdir -p "$DEMO_ELEVATION_WORK/prepared" &&
node docker/gpx_porto_taxi_dataset/elevation/download_demo_elevation.mjs "$DEMO_ELEVATION_WORK/tiles" &&
PYTHONPATH=docker/gpx_porto_taxi_dataset python3 -m elevation.build_demo_elevation \
  "$DEMO_ELEVATION_WORK/tiles" "$DEMO_ELEVATION_WORK/prepared/porto-elevation-120m.mtldem" --encoding webp &&
cp "$DEMO_ELEVATION_WORK/prepared/"{porto-elevation-120m.mtldem,porto-elevation-120m.json,elevation-attribution.json} \
  docker/gpx_porto_taxi_dataset/elevation/data/
```

Review and commit the prepared files together. The downloader rejects a changed
upstream source until its pinned ETag in the configuration is reviewed.

The builder also supports `--encoding numeric` for
an image-free format with predicted height differences compressed using XZ.
`benchmark_demo_elevation.py` compares five paired runs of the same 1,000 tracks,
including a fresh raster cache on every run; use `--alternate-raster` to compare
numeric encoding too.

Run Python tools as modules from `docker/gpx_porto_taxi_dataset`, for example
`python3 -m elevation.build_demo_elevation SOURCE OUTPUT` or
`python3 -m unittest elevation.test_demo_elevation`.

Local ARM64 Docker measurements (1,000 tracks, 49,798 points):

| Encoding | Raster size | Median creation | Tile expansion |
| --- | ---: | ---: | ---: |
| No elevation | — | 0.29 s | — |
| Lossless WebP | 17.07 MB | 0.57 s | 0.009 s |
| Numeric prediction + XZ | 16.66 MB | 0.83 s | 0.278 s |

Both encodings produce identical GPX files. WebP is the default because the
numeric format saves only 2.4% and expands more slowly. Heights occupy 69.7 MB
uncompressed, but only the tiles used by current tracks are expanded.
