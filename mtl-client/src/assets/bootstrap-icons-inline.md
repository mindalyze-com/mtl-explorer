# Bootstrap Icons — Inline Font (Offline Fix)

On iOS Safari, `@font-face` font requests bypass the service worker cache entirely, meaning the `.woff2` font file is fetched directly from the network even when a SW is active. This causes Bootstrap Icons to silently fail when the device goes offline — resulting in missing icons on buttons. Desktop Chrome and Safari are unaffected because they rely on HTTP cache, which persists offline.

The fix embeds the Bootstrap Icons `.woff2` font as a base64 data URI directly inside `bootstrap-icons-inline.css`, so no separate network request is ever made for the font. Since the CSS itself is bundled and cached normally, icons render correctly regardless of network state on all platforms, including iOS.
