# Frontend styling

PrimeVue uses the Lara preset customized in
[setupPrimeVue.ts](../../mtl-client/src/bootstrap/setupPrimeVue.ts). Its dark-mode
selector follows the app’s `data-theme` attribute. Shared colors, typography,
and layer tokens live in [base.css](../../mtl-client/src/assets/base.css);
shared classes live in [main.css](../../mtl-client/src/assets/main.css).

## PrimeVue components

Prefer the component’s design tokens for colors, dimensions, and spacing.
Inspect the installed `@primeuix/themes` preset and `@primeuix/styles` component
CSS before choosing a token. Token names become `--p-` CSS variables, such as
`button.padding.x` → `--p-button-padding-x`.

Overriding only a rendered handle’s width can leave token-based centering
calculations unchanged. Change the related tokens together when resizing a
PrimeVue control. Use scoped `:deep()` rules for integration or layout that
has no suitable token; verify the actual rendered component.

## Shared app controls

Use [MtlSlider](../../mtl-client/src/components/ui/MtlSlider.vue) for the app’s
single-value, range, and opacity sliders. It owns pointer and keyboard handling,
ARIA values, track layout, and touch targets. PrimeVue Slider tokens do not
resize this component.

Use [BottomSheet](../../mtl-client/src/components/ui/BottomSheet.vue) for mobile
tools, editors, and content panels. Keep panel sizing, dragging, scrolling, and
close controls in that shared component. Panels overlay the map without changing
map-control, attribution, or toolbar offsets.

Use the existing SectionHub, settings rows, and typography roles for new flows.
Keep entry-hub introductions subdued and informational, using the standard
heading and body typography. Put primary navigation in the rows below; keep
introductory links small and contextual. Do not add eyebrow labels or separate
caption styles. Place reusable styling at the shared owner; keep topic-specific
layout in its component.

Transient messages and persistent update prompts share
[AppNotifications](../../mtl-client/src/components/ui/AppNotifications.vue) and
[NotificationCard](../../mtl-client/src/components/ui/NotificationCard.vue).
Send messages through the toast service; use `warn` for warnings. Update prompts
provide actions and stay visible until the user acts. Keep one toast host so
messages stack without overlapping.

## Verification

Check the complete flow in both themes and at desktop and phone widths.
Verify keyboard focus, touch targets, long text, disabled states, and local
scrolling for tables or code. Use `pointer: coarse` when a change specifically
concerns touch input. Keep JavaScript and CSS layer ordering consistent.

Run relevant frontend checks from [the frontend workflow](frontend.md).
