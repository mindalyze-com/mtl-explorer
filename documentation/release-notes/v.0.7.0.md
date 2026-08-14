# MTL Explorer Release v.0.7.0

This release adds Imperial (US) measurement support and a redesigned filtering workflow. It also resolves both issues that were open in the public GitHub tracker at release time—[#4](https://github.com/mindalyze-com/mtl-explorer/issues/4) and [#5](https://github.com/mindalyze-com/mtl-explorer/issues/5)—along with all findings from the latest full regression.

## Features

- Added a persistent Metric and Imperial (US) measurement preference across maps, statistics, track details, charts, planning, filtering, and related views.
- Added unit-aware server milestones and consistent API measurement handling.
- Redesigned filtering with live updates, searchable views, scoped categories, stable map colors, pause and resume, reset with undo, and shared results across the app.
- Refined responsive workflows for Map settings, Admin, archive replay, Track Browser, and Track Details.

## Fixes And Changes

- Resolved all current open GitHub issues:
  - Fixed track-point popup values being clipped across browser layouts ([#4](https://github.com/mindalyze-com/mtl-explorer/issues/4)).
  - Added the requested Imperial unit option with persistent preferences ([#5](https://github.com/mindalyze-com/mtl-explorer/issues/5)).
- Fixed Filter and Statistics refresh after importing or deleting tracks.
- Preserved Statistics view and search state when opening and closing Track Details.
- Added exact activity-type filtering and improved category-selection behavior.
- Fixed temporary map visibility state not resetting when filters change.
- Corrected crossing calculations for reverse-indexed track sections.
- Correctly report failed and empty imports in Admin.
- Added a visible retry and dismiss notice when map configuration loading fails.
- Added Google Search Console verification.
- Expanded automated coverage and refreshed public documentation, generated API types, and regression evidence.
