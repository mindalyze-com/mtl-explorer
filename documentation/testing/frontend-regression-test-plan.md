# Frontend Regression Test Plan

High-level checklist for validating MTL Explorer after a large frontend refactor.

## Baseline Checks

| Area | Components / functions | What to verify |
|---|---|---|
| Static checks | `package.json`, `vite.config.ts`, `vitest.config.ts` | Run `npm run test`, `npm run type-check`, `npm run build`, and `npm run lint`. Note any pre-existing failures separately. |
| API type safety | `utils/openApiClient.ts`, `utils/apiClient.ts`, generated `x8ing-mtl-api-typescript-fetch` usage | Frontend code uses generated OpenAPI types and client APIs where available. No duplicated hand-written API shapes were introduced for changed contracts. |
| Browser coverage | Main app shell | Test desktop, narrow mobile, and touch input. Include a hard refresh, normal reload, and back/forward navigation. |

## App Shell And Session

| Area | Components / functions | What to verify |
|---|---|---|
| Routing | `router/index.ts`, `App.vue` | Auth guard redirects unauthenticated users to login. Authenticated users cannot stay on `/login`. Deep-link routes `/track/:id`, `/plan/:id`, `/stats`, and `/about` render without router errors. |
| Login | `views/LoginView.vue`, `utils/auth.ts`, `clientEnvironmentAnalytics.ts`, `mapConfigService.ts` | Local dev login succeeds with credentials from `mtl-server/src/main/resources/application-dev.yml`. Invalid login displays a usable error. Token storage, expiry handling, logout variants, and auth-failure redirects work. |
| Startup curtain | `views/HomeView.vue`, `utils/backgrounds.ts`, `utils/splashLogoPosition.ts` | Splash background/logo/message cycle render correctly. Curtain waits for track load, shows retry state on load failure, and preserves the login-to-home logo transition. |
| PWA/update handling | `App.vue`, `globalErrorHandlers.ts`, `useConnectivityProbe.ts` | Service worker registration does not loop. Update toast appears after auto-update reload. Connectivity probe failures do not block startup. |
| Branding/about | `utils/appBranding.ts`, `views/AboutView.vue`, `components/info/*` | Public copy uses "MTL Explorer". About/source overlay remains accessible without login and shows correct source/contact information. |

## Map And Track Loading

| Area | Components / functions | What to verify |
|---|---|---|
| Map initialization | `components/map/Map.vue`, `useMainMapController.js`, `mapStyleResolver.ts`, `mapConfigService.ts`, `mapStyle.ts` | Base and overlay maps load, stay synchronized, and recover from slow map config or tile-server readiness states. Map download/status banners appear only when appropriate. |
| Track collection load | `utils/tracks/trackCollectionLoader.ts`, `trackCollectionLoaderCore.ts`, `trackCacheDb.ts`, `trackApi.ts`, `trackFeatureAdapter.ts` | First load fetches paged track data, stores cache, emits loaded state, and shows accurate total/visible counts. Cached load works offline. Server freshness changes clear stale cache and reload tracks. |
| Precision upgrades | `useMainMapController.js`, `mapGeometry.ts`, `trackConstants.ts` | Low zoom uses overview geometry. Zooming in loads better precision without duplicate lines, stale points, or runaway requests. Abort/retry behavior works during fast pan/zoom. |
| Selection | `Map.vue`, `selectTrackById`, `selectTrack`, `deselectTrack`, `getTrackPopupMeta`, `showTrackSelectionPopup` | Clicking a single track highlights it and opens details. Clicking overlapping tracks opens the selection sheet. Deselect and sheet close restore map state. |
| Track points | `updateTrackPointsSource`, `showTrackPointPopup`, `fetchTrackCanonicalPoints` | Direction arrows appear at high zoom, honor visibility/opacity settings, and point popups show the correct metrics. |
| Swiss Mobility routes | `identifySwissMobilityRoutes`, `closeSwissMobilityPopup` | Nearby route popup displays expected routes and closes cleanly when changing tools or map selection. |

## Map Tools And Layers

| Area | Components / functions | What to verify |
|---|---|---|
| Navigation sheet | `components/ui/NavigationSheet.vue`, `BottomSheet.vue`, `usePointerDrag.ts` | Tool selection, active state, alert/drift badges, drag detents, close behavior, and mobile safe-area handling work. Only the intended tool is open at once. |
| Map settings | `MapSettingsPanel.vue`, `LayerControl.vue`, `useMapPreferences.ts`, `DEFAULT_LAYER_OPACITIES` | Theme changes, layer toggles, opacity sliders, reset, persistence, and basemap dimming survive reload. |
| Overlays | `applyActiveOverlays`, `onToggleOverlay`, `applyLayerOpacity`, `MAP_OVERLAYS` | Swiss/OSM overlay layers toggle independently, respect opacity, and retain correct ordering above/below tracks. |
| Globe mode | `GlobeControl.ts`, `updateGlobeState`, `toggleGlobeMode`, `applyGlobeProjection` | Globe auto-enter/exit thresholds work, manual disable is respected, and zoom limits do not trap the map. |
| Location search | `LocationSearchSheet.vue`, `useLocationSearch.ts`, `openLocationSearch`, `onLocationSearchSelect` | Search opens from the floating button, returns results, flies to the selected result, sets/clears marker, and hides when conflicting tools are active. |
| GPS locate | `components/gps/GpsLocate.vue`, `onLocationUpdate`, `onGPSDeviceEnabledDisabled` | Permission denied, enabled, following, drifted, and disabled states display correctly. Location marker updates without breaking manual map movement. |
| Media layer | `MediaOverlay.ts`, `MediaPreview.vue`, `mediaRepository.ts`, `createMediaLayer.ts` | Media layer toggles, thumbnails load, photo sheet opens, previous/next navigation works, and missing media shows a recoverable error. |
| Heatmap | `HeatmapOverlay.ts`, `onToggleHeatmapLayer` | Heatmap toggles without hiding tracks, updates after filters, and respects opacity. |

## Filters

| Area | Components / functions | What to verify |
|---|---|---|
| Filter shell | `Filter.vue`, `CustomFilter.vue`, `filterStore.ts`, `FilterService.ts` | Saved filter config hydrates from local storage, active chip state is correct, and closing the sheet refreshes state. |
| Filter catalog | `FilterCatalog.vue`, `FilterDetailPanel.vue`, `FilterActionBar.vue` | Filter list loads, search/grouping works, selecting a filter renders the correct parameters, apply/reset/cancel behave as expected. |
| Parameters | `GeoShapeParam.vue`, `filterParams.ts`, `FilterService.migrateFilterParams` | String, date-time, and geo params serialize to `FilterParamsRequest`. Legacy flat params migrate correctly. Empty/default params are pruned. |
| Geo drawing | `GeoDrawingOverlay.ts`, `onStartGeoDrawing`, `onGeoDrawUndo`, `onGeoDrawFinish`, `renderExistingGeoShapes` | Circle, rectangle, and polygon drawing complete with correct geometry. Undo, cancel, finish, clear, and persisted shape rendering work. |
| Styling and legend | `ColorPalette.ts`, `filterMetadata.ts`, `MapLegend.vue`, `ColorPaletteLegend.vue` | Categorical and gradient coloring match filter metadata. Legend sorting, hidden groups, collapsed state, and counts stay accurate after filter changes. |
| Filter application | `applyTrackFilter`, `onFilterApplied`, `orderLegendEntriesByFilterResult`, `applyGroupFilter` | Track visibility, counts, cache updates, group hiding, and map styling update without a full app reload unless required. |

## Track Browser, Statistics, And Details

| Area | Components / functions | What to verify |
|---|---|---|
| Track browser | `Statistics.vue`, `TrackBrowserControls.vue`, `TrackBrowserTable.vue`, `TrackBrowserSummaryBar.vue`, `useTrackBrowser.ts` | Search matches names, descriptions, dates, distances, durations, activity ids, and file paths. Sorts and summaries update with filtered rows. |
| Statistics | `Statistics.vue`, `StatisticsOverview.vue`, `fetchStatistics` | Overview metrics, charts, date ranges, activity breakdowns, and selected-track interactions render for empty, small, and large datasets. |
| Track details shell | `TrackDetails.vue`, `TrackDetailOverview.vue`, `TrackDetailQuality.vue`, `TrackDetailRelated.vue` | Details load by id, show loading/error/empty states, and switch tabs without refetch loops. Related tracks and quality fields render correctly. |
| Detail charts | `TrackGraph.vue`, `trackGraphConfigs.ts`, `TrackDetail*Graph.vue`, `chartSeriesAdapter.ts`, `chartTheme.ts`, `trackDetailsChartPointSettings.ts` | Elevation, speed, distance, and gain charts render, resample point counts correctly, and preserve theme/tooltip formatting. |
| Chart/map sync | `trackCursorSync.ts`, `useChartSync.ts`, `useTrackMapSync.ts`, `TrackDetailMiniMap.vue`, `useMiniMap.ts` | Hovering charts highlights mini-map points and vice versa. Drag, touch, resize, and tab changes do not leave stale cursors or listeners. |
| Shape previews | `TrackShapePreview.vue`, `readBestCachedTrackShape` | Mini shapes render in browser rows, filters, stats, related tracks, and selection sheets using the best cached geometry. |

## Measuring, Animation, And Virtual Race

| Area | Components / functions | What to verify |
|---|---|---|
| Segment measuring | `MeasureBetweenPoints.vue`, `DisplayMeasureResults.vue`, `fetchTrackIdsWithinDistanceOfPoint`, `fetchTrackDetailsForCrossingPoints` | Start/stop measure mode, point selection, crossing-point fetch, result grouping, and details links work. Tool cleanup removes markers/listeners. |
| Segment comparison | `SegmentCompare.vue`, `ComparisonChart.vue`, `MeasureGraph.vue` | Multi-track comparison renders charts/maps, aligns distances/time, handles missing data, and keeps selected racer state stable. |
| Virtual race | `VirtualRace.vue`, `RacerCard.vue` | Race playback, pause/reset, speed changes, mini-map movement, and racer ranking update correctly. |
| Map animation | `AnimateMap.vue`, `onAnimationStartEvent`, `onAnimationFinishedEvent`, `onAnimateEvent` | Animation can start, stop, and finish without leaving map gestures/tools in a disabled or inconsistent state. |

## Planner

| Area | Components / functions | What to verify |
|---|---|---|
| Planner shell | `PlannerTool.vue`, `PlannerToolbar.vue`, `LiveStatsBar.vue`, `ElevationProfile.vue` | Open/close planner, profile selection, live stats, elevation hover, and bottom sheet layout work on desktop and mobile. |
| Planner state | `usePlannerState.ts`, `routeHitTesting.ts`, `PlannerConstants.ts` | Add, insert, move, delete, clear, undo, redo, and viewport-too-large behavior work. Route recompute debounce, abort, retry, and segment-downloading states behave correctly. |
| Planner map interaction | `attachToMap`, `detachFromMap`, pointer handlers in `PlannerTool.vue` | Mouse and touch interactions place and drag waypoints, insert on route, suppress synthetic clicks, and re-enable map gestures after planner actions. |
| Planner persistence | `plannerRepository.ts` | Load config, save route, list plans, load saved plan, delete plan, download GPX, and prewarm endpoints work. Saved geometry displays immediately and recomputes after first edit. |
| Sidecar status | `useBRouterSegmentStatus.ts`, `fetchSidecarStatus` | Sidecar availability, pending downloads, and errors are visible but do not break existing planned routes. |

## Admin And Data Freshness

| Area | Components / functions | What to verify |
|---|---|---|
| Admin dialog | `AdminDialog.vue`, `GpxUploadTab.vue`, `IndexerStatusTab.vue`, `DataFreshnessTab.vue`, `ServerLogTab.vue`, `AttributionTab.vue` | Tabs open correctly. Upload, indexer status, data freshness, server log, attribution, and reload actions report success/error states. |
| Freshness polling | `useDataFreshness.ts`, `dataFreshnessStorage.ts`, `onDataFreshnessReload`, `showDataFreshnessBanner` | Banner appears when server token changes, dismiss expires, reload refreshes cache/data, and fresh login auto-refresh runs only once. |
| Indexer state | `useIndexerStatus.ts`, admin alert state | Pending/running indexer state updates the admin tool badge and does not block map interaction unnecessarily. |

## Shared Utilities And UI

| Area | Components / functions | What to verify |
|---|---|---|
| Formatting and math | `utils/Utils.ts`, `normalizeLatLng.ts`, `lineStringDeserializer.ts`, `mapGeometry.ts`, `routeHitTesting.ts` | Date, distance, duration, coordinate, line-string, bearing, bounds, precision, and route hit-test helpers handle nulls, invalid values, and boundary cases. |
| Preferences | `userPrefs.ts`, `useLocalStorage.ts`, `useTheme.ts`, `useHighchartsTheme.ts` | Legacy preference migration, read/write/remove, theme switching, and chart theme updates work across reloads. |
| Caches and diagnostics | `lowZoomCacheService.ts`, `cachingPmtilesSource.ts`, `backgroundCacheWarmer.ts`, `startupDiagnostics.ts` | IndexedDB failures degrade gracefully. PMTiles stale events trigger reload flow. Startup diagnostics toggle and logging do not leak sensitive data. |
| Responsive UI | `BottomSheet.vue`, `NavigationSheet.vue`, shared CSS | Text does not overflow controls. Sheets, tables, charts, and map controls remain usable at mobile widths and with touch input. |
| Error states | `ServiceHelper.ts`, `apiClient.ts`, `auth.ts`, all async components | 401/403 redirects, network failures, aborts, empty results, and server errors display recoverable UI instead of blank screens. |

## Suggested Regression Passes

| Pass | Scope |
|---|---|
| Smoke | Login, map load, filter apply/reset, select track, open details, open stats, open planner, run test/build commands. |
| Full desktop | Every section above in a desktop browser with normal network. |
| Full mobile/touch | Navigation sheet, bottom sheets, planner pointer handling, charts, map tools, GPS, and responsive layout. |
| Offline/cache | Load once online, reload offline, verify cached tracks, map cache behavior, freshness banner recovery after going online. |
| Data-change | Upload or re-index data, verify freshness detection, cache clearing, track reload, stats, filters, and details. |
