# PrimeVue Styling Guide

PrimeVue 4 uses a **design-token CSS variable system**. Every component property (sizes, colors, spacing, radii) is driven by a CSS custom property. Override the variable — don't patch the rendered DOM element.

---

## Where to find token names

### 1. Component CSS template
Shows every token used and how dimensions are calculated:
```
node_modules/@primeuix/styles/dist/<component>/index.mjs
```
Example for Slider:
```
node_modules/@primeuix/styles/dist/slider/index.mjs
```

### 2. Preset defaults (Lara is used here)
Shows the default values:
```
node_modules/@primeuix/themes/dist/lara/<component>/index.mjs
```
Example for Slider — defaults are `handle.width: 16px`, `track.size: 3px`.

### 3. Token → CSS variable name rule
Dots become dashes, prefixed with `--p-`:
```
slider.handle.width   →  --p-slider-handle-width
slider.track.size     →  --p-slider-track-size
button.padding.x      →  --p-button-padding-x
```

---

## The trap: manual `:deep()` overrides break PrimeVue's internal math

PrimeVue computes centering from its own tokens. Example for the slider handle:
```css
/* from @primeuix/styles/dist/slider/index.mjs */
.p-slider-horizontal .p-slider-handle {
    inset-block-start: 50%;
    margin-block-start: calc(-1 * calc(dt('slider.handle.height') / 2));
    margin-inline-start: calc(-1 * calc(dt('slider.handle.width') / 2));
}
```

If you do this:
```css
/* ❌ WRONG — handle grows but margin-block-start still uses the old token value */
.my-component :deep(.p-slider-handle) {
    width: 28px;
    height: 28px;
    margin-top: -14px; /* now you're fighting the framework */
}
```

The handle will be off-center because the `calc()` inside PrimeVue still reads the token, not your override.

Do this instead:
```css
/* ✅ RIGHT — PrimeVue recalculates centering automatically */
:root {
    --p-slider-handle-width: 28px;
    --p-slider-handle-height: 28px;
}
```

---

## Global touch/mobile overrides

Touch devices need bigger tap targets. Use `@media (pointer: coarse)` (not `max-width`) — it targets actual touch input, regardless of screen size.

All global PrimeVue token overrides go in **`src/assets/base.css`** — the single source of truth for design tokens.

```css
/* src/assets/base.css */
@media (pointer: coarse) {
    :root {
        --p-slider-handle-width:          28px;
        --p-slider-handle-height:         28px;
        --p-slider-handle-content-width:  16px;
        --p-slider-handle-content-height: 16px;
        --p-slider-track-size:            10px;
    }
}
```

This automatically applies to **every** `<Slider>` in the app with no per-component code.

---

## When `:deep()` IS appropriate

Use `:deep()` only for things that are **not** exposed as design tokens — typically colors that need to integrate with the app's own CSS variables:

```css
/* OK — colors are app-specific, not a global token override */
.my-wrapper :deep(.p-slider) {
    background: var(--slider-track);
}
.my-wrapper :deep(.p-slider-range) {
    background: var(--slider-gradient);
}
```

Do not use `:deep()` for sizes or positions — those are always token-driven.

---

## Quick reference: Slider tokens

| Token | CSS variable | Lara default |
|---|---|---|
| Track thickness | `--p-slider-track-size` | `3px` |
| Track background | `--p-slider-track-background` | `{content.border.color}` |
| Track border radius | `--p-slider-track-border-radius` | `{content.border.radius}` |
| Range fill color | `--p-slider-range-background` | `{primary.color}` |
| Handle width | `--p-slider-handle-width` | `16px` |
| Handle height | `--p-slider-handle-height` | `16px` |
| Handle background | `--p-slider-handle-background` | `{primary.color}` |
| Handle hover bg | `--p-slider-handle-hover-background` | `{primary.color}` |
| Handle dot width | `--p-slider-handle-content-width` | `12px` |
| Handle dot height | `--p-slider-handle-content-height` | `12px` |
| Transition duration | `--p-slider-transition-duration` | `{transition.duration}` |
