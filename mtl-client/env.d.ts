/// <reference types="vite/client" />
declare const __APP_VERSION__: string;
declare const __APP_PKG_VERSION__: string;

// Fallback shim for Vue SFCs that use the Options API (plain <script>, no lang="ts").
// vue-tsc cannot derive a typed default export for those; this gives consumers a
// generic component type instead of an implicit-any module error.
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

// colormap ships no types; treat as any.
declare module 'colormap';
