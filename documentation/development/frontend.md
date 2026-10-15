# Frontend development

The Vue, TypeScript, MapLibre, and PrimeVue PWA lives in `mtl-client/` and is served under `/mtl/`.

Generate the API package in the development container first, then run from `mtl-client/`:

```sh
npm install
npm run dev
```

Vite proxies `/mtl/api` to the local backend. Set `VITE_DEV_PROXY_TARGET` when using another backend address. The development service worker is disabled.

## User flows

Design the complete user flow before editing UI code; avoid narrow visual patches.
Follow the shared controls, panel behavior, and typography in [the styling guide](styling.md), and keep
changes consistent across the flow.

Domain calculations belong to the server; follow [backend ownership](backend.md#implementation).
Use [generated API types](api-generation.md) and [shared code rules](README.md#shared-code-rules).

## Stable test identifiers

Give major actions, navigation, inputs, panels, and important result/error states
stable `data-test` IDs. Add them during development and when filling regression
gaps. Decorative elements need none.

Use purpose-based names, such as `planner-save-plan`, stable across layout, theme,
and language changes. Put action IDs on interactive DOM elements in production
builds. Scope repeated controls by stable item keys, not list positions.

Keep accessible names and labels; test IDs supplement them. Update affected tests
when changing IDs. Follow the [browser locator rules](testing/regression.md).

## Profiling and checks

For browser profiling, use `profile` mode. It keeps the production runtime and
chunk layout, disables JavaScript and CSS minification, includes source maps,
and disables service worker generation and registration. Set `MTL_PROFILE_DIR`
to a dedicated output directory on your data drive, then run:

```sh
export MTL_VITE_CACHE_DIR="$MTL_PROFILE_DIR/vite-cache"
npm run build:profile -- --outDir "$MTL_PROFILE_DIR/dist"
VITE_DEV_PROXY_TARGET=https://your-backend.example npm run preview:profile -- \
  --outDir "$MTL_PROFILE_DIR/dist" --host 127.0.0.1 --port 5196 --strictPort
```

Open `/mtl/` on that port. Use a fresh origin or clear any existing service
worker before recording. Unminified assets increase load size; compare final
load timings with a normal production build.

Available checks:

```sh
npm run format:check
npm run type-check
npm run lint
npm test
npm run build
```

Run `npm run format` to format client source, tests, and configuration with the project Prettier settings.

`build` includes type checking; `build-only` only bundles assets. Edit source and `public/`, then rebuild. Do not edit copied server assets or `dist/`.

The [manifest](../../mtl-client/package.json) owns scripts and dependencies. Read [styling](styling.md), [API generation](api-generation.md), and [documentation authoring](website/README.md).

## Errors and diagnostics

Rethrow the original caught value with `throw error`. Do not replace it with
`new Error(String(error))`: that loses its type, stack, HTTP response, and
cancellation metadata. If a distinct user-facing message requires a wrapper,
use `new Error(message, { cause: error })` and retain the original failure.
Report the original transport error before reading private response details for
the UI. Keep credentials, response bodies, and private application values out of
diagnostics.

Use `startupLog` and startup timer `success` for normal startup, progress, and
sign-in transitions. Reserve warnings and errors for problems. Pass the actual
failure as the third argument to `startupWarn` / `startupError`, or the second
argument to timer `warn` / `error`; pass structured context separately after it.
Use `undefined` for the error when a warning has only context. Context objects
and timing metadata are not exceptions.

See [diagnostic logging](../architecture/diagnostic-logging.md).
