# Web analytics

Umami analytics is intended only for public MTL Explorer demo instances. It is
off by default on all installations, including demos. Each selected demo must
opt in explicitly; demo mode alone does not enable analytics.

Private installations should leave analytics disabled and can omit both the
script URL and website ID. They need no Umami script, `window.umami` object,
analytics server or external network access for this integration. The client
checks only its own server's public configuration endpoint. Disabled or incomplete
settings load no external script or analytics hook.

Add these environment variables to the **app container**, then restart it:

```yaml
MTL_ANALYTICS_UMAMI_ENABLED: "true"
MTL_ANALYTICS_UMAMI_SCRIPT_URL: "https://stats.mtl-explorer.com/script.js"
MTL_ANALYTICS_UMAMI_WEBSITE_ID: "YOUR-WEBSITE-UUID"
```

The equivalent server settings are `mtl.analytics.umami.enabled`, `script-url`
and `website-id` in `application.yml`. Use the website ID for this installation from
Umami. The ID is public; dashboard credentials and API tokens are not needed.
The script URL must use HTTPS without credentials, query parameters or fragments.
Missing or invalid settings leave analytics disabled and produce a server warning.

Set `MTL_ANALYTICS_UMAMI_ENABLED: "false"` and restart to disable it. Newly loaded
pages then receive disabled configuration. Already open pages need a reload.
No frontend rebuild is needed when changing the settings.

The public `/mtl/api/analytics/umami-config` endpoint returns only the effective
settings and is not cached. The server permits the configured script origin in
its content security policy only when analytics is active. The tracker loads
once, asynchronously after page load, and handles navigation within the app.
Failed configuration requests or a blocked/unavailable tracker do not block the app.
This includes a missing configuration endpoint, a missing script, DNS/network
failures and a script request that never finishes. Analytics runs after app mount;
authentication, navigation and app interactions do not wait for it. A missing
`window.umami` is ignored. The server validates settings locally without contacting
the analytics server.

Pageviews use Umami's default anonymous visitor and session identification.
The client supplies no Distinct ID or login reference, calls no `identify`
method and stores no custom analytics ID in local or session storage.

Pageviews retain one event per route change. Before sending, dynamic URLs are
grouped as `/mtl/track/:id`, `/mtl/plan/:id`, `/mtl/review/:id` and
`/mtl/admin/:section`. The same request carries `track_id`, `plan_id`, `source_id`
or `admin_section` in its `data` object. These values are strings; titles,
coordinates and query parameters are not added. Internal referrer URLs use the
same grouping. The browser URL and navigation do not change, and grouping adds
no requests or custom events. Routes without a parameter keep their own URL.

Query parameters and fragments are excluded. Do Not Track prevents the tracker
from loading and blocks analytics payloads.
The About panel shows an analytics notice when it is configured.

The installed user and administration guide follows the same optional setting.
Its local `guide-analytics.js` loader checks `/mtl/api/analytics/umami-config`
after page load. Disabled, incomplete or unavailable settings load no external
tracker. Enabled guide pages use the configured tracker and website ID with
Umami's default identification. Each HTML guide page records one pageview;
query parameters and fragments are excluded, and Do Not Track prevents tracking.
Configuration and tracker failures never block guide navigation. The guide does
not read login tokens or store analytics IDs.

The public website guide uses `site-analytics.js` and the same default
identification; see [website generation](generation.md).
See [Umami metric definitions](https://docs.umami.is/docs/metric-definitions),
[tracker options](https://docs.umami.is/docs/tracker-configuration) and
[single-page apps](https://docs.umami.is/docs/guides/track-single-page-apps).
