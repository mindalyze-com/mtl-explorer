# Bootstrap Icons — Inline Font (Offline Fix)

The app embeds the Bootstrap Icons font to avoid a separate font request during
offline use. This addressed observed missing button icons on iOS Safari; browser
cache behavior can vary by version and device.

`bootstrap-icons-inline.css` contains the `.woff2` font as a base64 data URI and
is imported by the app bootstrap. Offline icons therefore depend on the cached
app stylesheet, without fetching another font file. This does not make an
uncached first visit available offline.
