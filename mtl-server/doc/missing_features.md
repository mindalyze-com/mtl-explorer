# MTL Explorer - Feature Gap Analysis

Updated 23 May 2026 after reviewing the current feature overview and the older
feature-analysis notes.

MTL Explorer already has a strong base: self-hosted import, full archive map,
filters, heatmap, local media, track details, energy/power estimates,
Exploration Score, Segment Analyzer, Virtual Race, statistics, Garmin import,
admin tooling, BRouter planning, saved plans, GPX upload/export workflows,
offline-capable client cache, and local map options.

The best missing additions are not generic fitness-app parity items. The
highest-value direction is to use the existing archive, exploration, planner,
media, and statistics data to create more personal recurring value.

## Highest Impact

| Rank | Addition | What's Missing | Why Users Would Like It |
|---:|---|---|---|
| 1 | New-ground route suggestions | Suggest routes from a start point that maximize unexplored ground, using Exploration Score history, BRouter, and waymarked trail context. | This is highly aligned with MTL Explorer's unique archive and exploration model. |
| 2 | Personal records and milestones | Automatic records such as longest ride, biggest climb, fastest 5 km/10 km, best segment, most new ground, best month, and first visit to an area. | Gives users reasons to revisit old data and makes the archive feel alive. |
| 3 | Year and season recaps | Dedicated yearly/seasonal summary with totals, maps, PRs, new-ground highlights, activity mix, favorite areas, and media. | Turns existing statistics into a memorable review users will actually open and share as screenshots. |
| 4 | FIT sensor charts | Heart rate, cadence, temperature, device power, HR zones, and sensor availability indicators where source files contain the data. | Garmin/FIT users expect this; it makes imported device data feel complete. |
| 5 | Track cleanup editor | Trim start/end, crop bad GPS sections, split, merge, correct activity type, and hide or mark bad ranges. | Historical archives often contain messy recordings; cleanup improves trust in all derived stats. |
| 6 | Automatic splits and laps | Per-km/per-mile tables with pace, elevation, HR/power where available, stops, and comparison to previous splits. | Standard in running/cycling tools and useful for hikes, climbs, and repeated routes. |
| 7 | Exploration goals | Goals such as "new ground this year", "explore this region", "visit 100 km of new trails", or "reduce unexplored gaps near home". | Builds directly on Exploration Score instead of copying generic distance goals. |
| 8 | Media timeline in track detail | Place photos and videos on the track detail timeline, elevation chart, and map position for the selected activity. | Makes MTL Explorer feel like a private outdoor memory archive, not only an analytics tool. |
| 9 | Gear and equipment tracking | Assign shoes, bikes, tires, chains, skis, or other equipment to activities; track distance, time, service, and retirement thresholds. | Practical for runners and cyclists, and creates recurring maintenance value. |
| 10 | Tags and collections | Free-form labels and collections such as holiday, commute, with kids, race, favorite, project, or trip. | Helps users search by memory and intent, not only by numeric filters. |

## High-Medium Impact

| Rank | Addition | What's Missing | Why Users Would Like It |
|---:|---|---|---|
| 11 | Private share links | Tokenized links for a track, plan, route recap, or selected media, with optional expiry. | Useful without turning MTL Explorer into a social network. |
| 12 | Weather and daylight context | Historical temperature, rain, snow, wind, daylight, sunrise/sunset, and maybe weather icons on tracks. | Explains why an activity felt hard and enriches old memories. |
| 13 | Surface and trail-type analysis | Road, gravel, trail, singletrack, paved/unpaved, MTB/hiking route network overlap, and surface percentages from OSM/route data. | Strong for planning, cycling, running, hiking, and route comparison. |
| 14 | Side-by-side activity comparison | Static comparison of two activities or segment attempts: route, elevation, pace, power, stops, HR, photos, and deltas. | Complements Virtual Race with a calmer analysis view. |
| 15 | Smart archive search | Guided search/query builder for questions such as "hikes near Lucerne in July with more than 1000 m ascent and photos". | Makes the existing filter power easier to use for non-SQL users. |
| 16 | Richer personal heatmap controls | More controls by activity, date range, season, time of day, intensity, and selected region. | Heatmap exists, but richer controls would make it a discovery tool rather than a simple layer. |
| 17 | Dashboard and activity timeline | Recent activities, weekly summary, import status, exploration highlights, goals, gear alerts, and notable records. | Better recurring landing screen once the user has an established archive. |
| 18 | Route cue sheet and route card | Printable or exportable route summary with distance, ascent, profile, waypoints, route notes, and caution/disclaimer text. | Useful for planning while staying honest that MTL Explorer is not safety-critical navigation. |
| 19 | Turn cues for planned routes | Turn-by-turn or cue-point generation for planned routes and Garmin/device export. | Improves the planner-to-device workflow, but requires careful routing metadata handling. |
| 20 | Fitness/fatigue trends | CTL/ATL/TSB-like views using existing training load where available, with clear caveats around estimates. | Useful for training-oriented users, but should be framed conservatively. |

## Medium Impact Or Later

| Rank | Addition | What's Missing | Why Users Would Like It |
|---:|---|---|---|
| 21 | Offline mobile recording | PWA track recording with pause/resume, local save, and later sync/import into the archive. | Valuable, but it shifts MTL Explorer toward being a recorder rather than primarily an archive. |
| 22 | External platform sync | Optional imports from Strava, Komoot, Suunto, Apple Health, or other export APIs. | Good for consolidation, but API policy, OAuth, and maintenance cost are real. |
| 23 | Multi-user or household mode | Separate users, permissions, shared plans, shared media, and per-user statistics. | Useful for families or clubs, but it touches auth, privacy, filtering, and statistics deeply. |
| 24 | Notifications | Import finished, weekly recap ready, goal reached, gear service due, or exploration milestone reached. | More useful after goals, gear, and recaps exist. |
| 25 | Accessibility and keyboard shortcuts | Keyboard navigation, focus management, ARIA labels, and shortcuts for common flows. | Quality improvement that helps power users and accessibility at the same time. |
| 26 | 3D terrain view | Terrain exaggeration and 3D map mode where DEM data is available. | Good for demos and mountain routes, but less core than archive intelligence. |
| 27 | Internationalization | Translation infrastructure, locale selection, and maintained UI strings. | Helps adoption outside English-speaking users, but adds ongoing maintenance. |
| 28 | Public profile or social feed | Public activity feed, comments, following, likes, and profiles. | Low fit with the private self-hosted positioning; private share links should come first. |

## Quick Wins

These look like the best effort-to-value candidates if the goal is a visible
user improvement without a major architecture shift.

1. Automatic km/mile splits table.
2. Media timeline inside track detail.
3. Keyboard shortcuts for detail navigation, close, replay, and planner actions.
4. Basic tags/favorites on tracks.
5. Personal record cards from already-computed metrics.
6. Year recap page using existing statistics and map data.
7. Track hover preview with mini elevation/profile summary.
8. Richer heatmap controls over the existing heatmap layer.

## Stale Items From Older Gap Notes

The older 30 March 2026 list included several items that are now documented as
present or partly present in the current feature overview. Treat these as
implemented, partially implemented, or no longer good headline gaps unless code
inspection proves otherwise:

- Route planning with BRouter, saved plans, live route geometry, and GPX export.
- Heatmap layer.
- Admin GPX upload.
- Track browser with search, sort, pagination, shape previews, and detail navigation.
- Training-load-like statistics and energy/power trends.
- Planner request retry/abort/status handling.
- Local location search.
