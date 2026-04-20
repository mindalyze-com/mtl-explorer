import type { App } from 'vue';

/**
 * Centralizes Vue's component error handler plus window-level uncaught error
 * and unhandled-rejection listeners. Extracted from main.ts.
 *
 * Catches silent failures that never reach component code so they show up in
 * the console with a recognizable [MTL] prefix.
 */
export function installGlobalErrorHandlers(app: App): void {
  app.config.errorHandler = (err, _instance, info) => {
    console.error('🚨 [Vue Error] Component failed to render or execute:', err, info);
  };

  if (typeof window !== 'undefined') {
    window.addEventListener('error', (event) => {
      console.error(
        '[MTL] Uncaught error:',
        event.message,
        '| source:',
        event.filename,
        '| line:',
        event.lineno,
        event.error
      );
    });
    window.addEventListener('unhandledrejection', (event) => {
      console.error('[MTL] Unhandled promise rejection:', event.reason);
    });
  }
}
