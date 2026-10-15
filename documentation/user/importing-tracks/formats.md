# Supported GPS formats

MTL Explorer reads GPX, FIT, TCX, KML/KMZ, IGC, NMEA, GeoJSON, and GDB files.

What you get depends on what the file contains:

| The file contains | What you get |
| --- | --- |
| Positions with timestamps | The track on the map, plus time, speed, and other time-based results |
| Planned routes (no timestamps) | The route on the map; no duration or speed |
| No elevation | No elevation results |
| Several recordings or gaps | Separate parts; gaps are not filled in |
| Heart rate, cadence, or power | Not imported; keep the original file. Heart rate can come from [Google Health](../health/track-matching.md). |

MTL Explorer can't add data the file doesn't have. If something looks wrong,
check the track's [review findings](../tracks/source-review.md).

You can always download the original file, as long as it is still in the GPS folder.
