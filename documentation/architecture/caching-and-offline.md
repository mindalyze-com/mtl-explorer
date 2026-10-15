# Browser caching and offline behavior

The PWA shell, track cache, map archive cache, media responses, and browser preferences have separate lifecycles. Cached display data is not an alternative owner of server calculations.

Server/client freshness domains compare revision tokens after ingest and job changes. New requests must not let older results restore a superseded selection. Source changes invalidate exact segment attempt references and relevant derived results.

A cache-first archive can show previously available tracks while refreshing. Uncached content and server-backed calculations still need connectivity. Hosted terrain, overlays, and account services can have independent network requirements.

Service-worker updates wait for an explicit reload. Session cleanup can remove browser data; credentials-only sign-out has a narrower scope.

[Source acknowledgements](source-issues.md) belong to the server account and
survive either sign-out flow. They do not dismiss processing, offline, service,
or stale-map indicators. Freshness detection and reload remain available without
a snooze or dismiss handler.

See [PWA configuration](../../mtl-client/vite.config.ts), [track cache](../../mtl-client/src/utils/tracks/trackCacheDb.ts), and [user limits](../user/mobile-and-offline.md).
