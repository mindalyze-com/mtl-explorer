# HTTPS and reverse proxies

MTL Explorer serves plain HTTP on port `18080`. To reach it securely from
outside your home network, put a reverse proxy with HTTPS in front of it, such
as Caddy, Traefik, or nginx.

HTTP/1.1 is supported. HTTP/2 is optional.

1. Point your hostname to the app's port `18080`.
2. Set up a valid certificate in the proxy.
3. Keep the `/mtl/` path, and forward the original host and scheme (`https`).
4. Keep streaming and range requests working; photos, videos, and maps need them.
5. Allow long requests: planning a long route can take up to five minutes.
6. Open `https://<your-host>/mtl/` and test the login, the map, a photo
   download, and route planning.

If something does not load, check the browser console and the
[server logs](../maintenance/logs.md) for path or mixed-content errors.

For Google Health, the connection uses a manual copy-and-paste step that
works behind a proxy; see [Google Health setup](../google-health.md).
