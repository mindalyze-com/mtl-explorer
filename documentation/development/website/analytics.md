# Web analytics

Umami analytics is intended only for public MTL Explorer demo instances. It is
off by default on all installations, including demos. Each selected demo must
opt in explicitly; demo mode alone does not enable analytics.

Private installations should leave analytics disabled and can omit both the
script URL and website ID. They need no Umami script, `window.umami` object,
analytics server or external network access for this integration. The client
checks only its own server's public configuration endpoint. Disabled or incomplete
settings load no external script, install no analytics hook and read no client login reference.

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
`window.umami` or `identify` method is ignored. Thrown errors and rejected promises
from `identify` are caught. The server validates settings locally without contacting
the analytics server.

Basic pageviews use the current MTL Explorer `ClientLoginRef` (`client_login_ref` JWT claim) as
Umami's Distinct ID through `umami.identify(id)`. The before-send hook also sets
the ID on the first pageview and follows login, logout and login expiry on
subsequent events. Identification sends no JWT, username or extra login data.
Without a valid client login, pageviews carry no Distinct ID. The reference
identifies one successful login and changes on the next login.

There are no custom events. Query parameters and fragments are excluded, and
Do Not Track prevents the tracker from loading.
The About panel shows an analytics notice when it is configured.
See [Umami Distinct IDs](https://docs.umami.is/docs/distinct-ids),
[tracker options](https://docs.umami.is/docs/tracker-configuration) and
[single-page apps](https://docs.umami.is/docs/guides/track-single-page-apps).
