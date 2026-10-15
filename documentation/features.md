# Features

MTL Explorer keeps your GPS tracks, photos, videos, and optional health data on
your own server. This list describes the version in development; see the
[released guides](versions/README.md) for older versions.

| Topic | What you can do | Guide |
| --- | --- | --- |
| Import | Upload files or copy them into a folder; GPX, FIT, and six other formats; optional Garmin download; files added while the server was off are picked up | [Import tracks](user/importing-tracks/README.md) |
| Map | See all your tracks on one map; click any spot to see tracks, photos, places, and public trails there; choose base maps, trail and place layers, and a heatmap | [Map](user/map.md) |
| Filters | Choose tracks by date, activity, and other criteria; group and color them; write your own filters in SQL | [Filters](user/filters/README.md) |
| Tracks | Search and sort your tracks; see summaries, graphs, related tracks, data quality, photos, breaks and GPS gaps; download the original file | [Tracks](user/tracks/README.md) |
| Review | Check files with problems, look at the original GPS points, fix files in another tool, and choose whether a track counts on the map, in statistics, or both | [Review](user/tracks/source-review.md) |
| Statistics | Totals, activity types, highlights, trends over time, photo counts, milestones, and the tracks behind each number | [Statistics](user/statistics/README.md) |
| Energy and motion | Estimated calories, work, and power; training load; moving, stopped, and unknown time; breaks | [Analysis](user/analysis/README.md) |
| Exploration | See how much of an activity was new ground compared with your earlier tracks | [Milestones and exploration](user/statistics/milestones.md) |
| Segments | Mark a stretch with checkpoints, find every time you passed it, and compare or replay your attempts, including heart rate | [Segment discovery](user/analysis/segments.md), [comparison](user/analysis/comparison.md) |
| Replay | Replay one activity in 3D, race several attempts, or animate your archive over time | [Replay](user/replay.md) |
| Planning | Plan routes on the map with live distance, climb, and elevation; undo and redo; save plans and places; export GPX | [Planning](user/planning.md) |
| Media | Browse photos by map, activity, or period; full-screen viewer; download originals; correct camera time; set locations; play videos, converting them when your browser needs it | [Photos and videos](user/media/README.md) |
| Health | Optional read-only copy of your Google Health data: trends, records, exercise routes, and heart rate on tracks | [Health](user/health/README.md) |
| Preferences | Theme, date and number format, units, and signing out with or without clearing local data | [Settings](user/settings.md) |
| Mobile and offline | Use it on your phone, show your position, install it as an app, and view already loaded tracks and maps offline | [Mobile and offline](user/mobile-and-offline.md) |
| Administration | Data folders, import progress, rescans, logs, component versions, update checks, backups, and updates | [Administration](administration/README.md), [Logs](administration/maintenance/logs.md) |

## Limits

- MTL Explorer can't add data your files don't have, such as missing timestamps
  or elevation.
- Energy and exploration are estimates.
- Duplicates, and tracks excluded from statistics in Review, don't count there.
- Route planning needs downloaded routing data. Online maps, 3D terrain, and
  trail layers need the internet.
- Offline, you only see what this browser already loaded. MTL Explorer does not
  record activities.
- Google Health is in beta and needs your own Google API access. Its dates are
  separate from the track filter.
- Photo counts follow the time span of your filtered tracks and the
  **All media** / **Track related** choice.

## Demo

The public demo uses [public Porto taxi tracks and generated data](../docker/gpx_porto_taxi_dataset/DATASOURCE.md).
Its health data is made up. You can't connect a Google account in the demo.

For how things work inside, see [Architecture](architecture/README.md). For
building and testing, see [Development](development/README.md).
