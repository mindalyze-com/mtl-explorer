# Map exploration interactions

The 2D map uses one dispatcher for exploration. Layers supply targets; the
shared content sheet presents them. Drawing, measurement, and planning retain
their own gestures. The separate 3D replay map does not use this dispatcher.

## Small dispatcher contract

`mapInteractionDecision.ts` defines the target union, semantic identity,
pointer/touch tolerance, deduplication, and presentation decisions.
`mapInteractionCoordinator.ts` owns click lifetime and incremental source state.
Neither module imports Vue or MapLibre at runtime.

Map consumers register with `registerConsumer`, which returns a cleanup function.
The controller exposes `registerMapInteractionConsumer` for additional map
components; registrations can precede map initialization and survive renderer
recreation until their component calls cleanup. Map children can use
`useMapInteractionConsumer` to access the provided dispatcher and clean up on
unmount:

- A local consumer collects candidates synchronously from the captured click.
- An asynchronous consumer supplies a cancellable provider with a bounded timeout.
- Either consumer can declare eligibility against the captured context.

Registration and cleanup invalidate an existing interaction. The main map adapter
registers the fixed POI, track-point, photo, filtered-track, Swiss-route, and enabled Waymarked
activity consumers. This is a candidate dispatcher; existing type-specific handlers still
open details and photo collections. Adding a consumer does not require another
map click listener or presentation surface.

`useMapInteractions.ts` binds the dispatcher to map events and existing handlers.
`useMainMapController.ts` binds invalidation to tool ownership, track filter
revision, media scope, and layer settings. `Map2DRenderer.vue` hosts one exploration
`BottomSheet` and a separate foreground GPS detail `BottomSheet`;
`MapTargetChooser.vue` starts with a bounded **All** overview.
`mapTargetCategories.ts` groups presentation by target kind and trail source,
without changing consumer registration or candidate identity. Waymarked
activities share one category, as do Swiss route layers. Unknown registered
sources remain accessible through their own category.

All is the initial chooser view. A ten-row preview budget shares space across
categories, with at most three targets in each category,
followed by a counted more row.
Small sets within those bounds show every target directly.
Categories with results, pending lookups or lookup issues stay reachable through
wrapping, persistent chips. A summary or
chip opens 20 targets per page, with every returned target accessible. Photo counts
distinguish collection targets from individual photos. Pending, unavailable,
and cancelled categories remain visible; completed empty categories are hidden.

Available screen distances rank an initial batch. GPS peers use available server
proximity distances, then canonical recording time, newest first. Localized display
dates are never parsed; equal or
unavailable values keep supplied order. Late arrivals preserve previews, category
order, the current page, and focus. A new click resets navigation; selected content
remains mounted through Back to results and reopening its result row. Category
controls appear only in the chooser; selected details use the shared sheet's header Back action.
The sheet's optional `backLabel` and `back` event keep navigation with the caller.
Track details emit the same generic event without knowing their return destination.
GPS rows share `TrackChoiceRow` with the photo activity picker. The row contains
a title and compact activity/date/distance/ascent facts, plus available photo counts.
Canonical timestamps and measurements are formatted reactively through the app's
locale/unit helpers; total `lengthMeters` is distinct from hit `distanceMeters`.
`TrackChoiceFacts` provides the compact metric badges for archived GPS rows.
Proximity distances
are used for ordering and are not displayed in these map lists. Its lazy cached
`TrackShapePreview` appears on the right, before the chevron, only when the list
container is at least 40 rem wide. A track icon remains on the left at every width.
The sketch uses a subdued theme-aware blue, a 1.75 px stroke and no opacity fade.
Mouse/pen hover and keyboard focus reuse
the existing track-highlight source without changing selection, viewport or URL.
Leaving the row, hiding the chooser, changing the click or closing it clears the
preview while retaining the selected track highlight. Routine geometry refreshes
retain an eligible preview. Touch does not preview.

The chooser uses a fixed shared-sheet detent capped at 30 rem or 60% of the
viewport, whichever is smaller. Its height stays constant through source updates,
category changes and paging. Manual dragging and maximization remain available.
GPS details open at the expanded 92vh detent and standard width on desktop.
On mobile they maximize to 100dvh and full width. GPS details use a separate
foreground sheet. The dispatcher stays mounted at its unchanged height, with
its chooser inert while covered. Foreground opening covers at least the underlying
sheet's height; explicit detail resizing is still allowed. Back, Close and browser
Back hide GPS details and reveal the unchanged dispatcher, preserving its
category, page and manual size. Retained GPS content stays mounted for reopening.
GPS Close uses the sheet's `deferClose` and `close-request` contract to request
the same return navigation as Back. The panel closes only after navigation commits.
Pending or failed returns preserve its size and maximized state; Close can be retried.
Drag-to-close restores the pre-drag height while waiting for navigation.
Other detail types retain the dispatcher's current panel size. The chooser adds no scroll container or
independent height calculation. Standalone GPS details also open expanded.
When a tool such as Statistics or Filter opens GPS details, route synchronization
keeps the originating tool open underneath. The return target retains its
navigation state; returning reveals the existing panel without reopening it.

The GPS sheet shares its layout with nested media viewers only in map exploration.
Statistics, Filter and direct-link media viewers keep their own viewport centering
and size presets. Ordinary media viewing leaves the GPS sheet's height unchanged;
the compact background detent is enabled only while 3D replay is active.

## Lifetime and presentation

Every accepted exploration click opens **Choose what to see here** on All, even
with zero or one candidate. Only explicit row selection opens details. Pending
categories show a spinner and one skeleton row while known hits remain clickable.
Completed empty categories have no chip or All-list group. If the selected category
finishes empty, the chooser returns to All and focuses its chip. A confirmed empty location
shows “Nothing found here”; unavailable and cancelled sources remain distinct.

Filtered track proximity and route identification settle independently. GPS
lookup starts existing ID and nearby-media lookups in parallel. IDs and processing
notices appear immediately; optional facts enrich existing rows without changing
their order or selection. Media facts have a bounded three-second timeout; failure
keeps GPS IDs and processing notices without reporting missing facts as zero. Late hits
append without changing category navigation, visible ordering or selected details.
A point's parent track remains an action of the point rather than another overlap
row. Photo and cluster taps publish the primary collection and one captured
**Map view at tap** choice directly under Photos. Fully known equal ID sets are
deduplicated; incomplete cluster membership retains both scopes. Collection counts
do not sum overlapping photo totals. Each row opens the existing viewer directly,
with no secondary photo chooser or associated-GPS lookup in this dispatcher flow.
Viewport selection uses captured IDs without expanding cluster leaves. Both scopes
carry the original map bounds for the viewer's overview; legacy callers retain
their existing collection chooser and current-map fallback.

Full GPS details use the existing `/track/:id` route. `useMapInteractionTrackRoute`
keeps the return context in the caller, separate from the dispatcher and detail
components. Header Back and browser Back restore retained chooser navigation;
Forward restores the same selected content. Reload or a direct link uses standalone
track routing, with Back returning to an available overview or the map. Only track
identity goes in the URL, and superseded or rejected navigation cannot restore an
old selection. Points and photo/POI/public-route results retain the map URL until
the user explicitly opens full GPS details.

Each accepted click captures coordinates, actual pointer/touch/pen input, filter
revision, media scope, and eligibility. Requests check both interaction identity
and cancellation. Manual movement cancels unfinished discovery but retains the
original click, collected targets, and selected detail loads. Programmatic focus
movement preserves discovery. Double-click zoom skips the second exploration
click and clears only the gesture's interaction.

`useMapInteractionVisibility` listens to the shared sheet's actual layout. Once
its opening geometry settles, an occluded click gets the minimum vertical pixel
pan needed to leave 32 px above the chooser. Zoom, bearing and pitch stay fixed;
the original discovery context is retained. A clear marker requires no movement.
Each click gets one attempt, including clicks while the chooser stays open.
Manual movement, early sheet resizing, selection and close cancel that attempt;
late results, category changes and returning from details do not move the camera.

Providers time out after 15 seconds. Failure, timeout, cancelled discovery, incomplete track
processing, and confirmed empty results remain distinct. One failed consumer does
not discard other results. Close, a new click, invalidation, style replacement,
and disposal prevent late responses from reopening content.
Ending retained results also discards GPS details hidden by **Back to results**;
they must not reopen as standalone content. Visible GPS details may continue on
their standalone route when the discovery context is invalidated.

For retained photo-cluster targets, the media overlay keeps the original source
tree until the interaction ends. Viewport refreshes queue their latest data during
that interval. This preserves collection identity and pagination after panning;
no complete leaf expansion is needed. Invalidation closes a photo collection
whose pagination depended on that retained tree.


## Public trail details

`waymarkedTrailProvider.ts` captures the click tolerance through public map
unprojection before any request. It intersects the frozen Mercator footprint with
Waymarked Trails geometry tiles and resolves every matched top-level relation
through `list/by_ids`. This avoids the area endpoint's 100-result cap. Adjacent
tiles and repeated segments are deduplicated. Metadata requests use batches rather
than limiting the number of targets.

Geometry requests are limited to 16 tiles per activity and use a bounded five-minute
memory cache. Wider clicks show **Zoom in to identify Waymarked Trails routes**,
with unavailable status rather than an empty result. Names, references, itineraries,
known network labels, and official route-page links appear in the existing flow.
`swissRouteDetails.ts` fetches `htmlPopup` only after a Swiss feature is selected.
It extracts the published SchweizMobil stage and full-route URLs from inert markup.
Stage details is the primary link when present. Unnamed swisstopo segments expose
available classification and a Swiss map link; missing names or homepages are not
invented. Selected detail requests have a timeout and interaction/selection guards,
and ordinary panning preserves them.

The grouped chooser has no archive actions. Download and 3D replay remain in
selected archived track content. `MapOverlayDetails.vue` displays only external
route fields and validated links.

Selected routes use a dedicated detail layout in that same sheet: a wrapped
route name, activity icon, metadata card and primary external action. The first
available validated link is primary, with Swiss stage details preferred.
`MapPoiDetails.vue` presents mapped place types with their existing category
icons and colors, plus elevation when available. Its pure presentation helper
also supplies the sheet header icon. Both use the shared compact desktop width
and content fitting. These panels add no lookups or extra sheets.

API contracts: [Waymarked geometry](https://github.com/waymarkedtrails/waymarkedtrails-api/blob/master/wmt_api/api/routes/tiles.py),
[route listings](https://github.com/waymarkedtrails/waymarkedtrails-api/blob/master/wmt_api/api/routes/listing.py),
[Swiss HTML popups](https://docs.geo.admin.ch/access-data/get-html-popup.html).
