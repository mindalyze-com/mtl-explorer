# Mobile segment phone mockup

`mobile-segments-screen.webp` is the unchanged 390 × 844 screenshot from the
isolated public-demo backend. See the
[capture and data notes](../../../../docs/assets/feature-showcase/README.md).

The user requested the original phone frame on 2026-09-06. The built-in
ImageGen tool combined this screen with the previous phone mockup from
commit `4221f3ae`. The final 853 × 1844 image is saved as WebP under
`docs/assets/feature-showcase/large/mobile-segments.webp`, with 520 px preview
and 360 px thumb variants. No private data was used.

## Final edit prompt

Use case: compositing. Asset: MTL Explorer website mobile screenshot in a phone
mockup. Image 1 is the EDIT TARGET and reference for the black phone body, subtle
perspective, rounded glass, metal edge, side buttons, white studio background,
and soft floor shadow. Image 2 is the exact NEW SCREEN CONTENT, a real screenshot
of the current MTL Explorer segment replay using public demo tracks. Replace
only the old screen content in Image 1 with the entire screenshot from Image 2.
Preserve Image 2 as an intact raster insert: all UI labels, text, numbers, map
geometry, blue tracks, replay position, controls, spacing, and colors must
remain unchanged; only uniform scaling and perspective placement to fit the
physical display are allowed. Do not reuse or redraw the outdated UI from
Image 1. Keep the full new screenshot visible; leave a small white safe area
above and below it for the phone camera cutout and home indicator, so those
hardware elements do not obscure the UI. Keep the same upright black phone
and slight left edge visible as Image 1, fully in frame on clean white, with
comparable margins. No hands, stands, added text, or decorative elements.
Portrait output with approximately the same 520:1124 aspect ratio as the
original website phone image, high resolution.
