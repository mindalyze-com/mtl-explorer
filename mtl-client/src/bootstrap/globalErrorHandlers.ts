import type { App } from 'vue';
import { logSanitizedError, sanitizeForLog } from '@/utils/safeLogging';

let consoleRedactionInstalled = false;

/**
 * Centralizes Vue's component error handler plus window-level uncaught error
 * and unhandled-rejection listeners. Extracted from main.ts.
 *
 * Catches silent failures that never reach component code so they show up in
 * the console with a recognizable [MTL] prefix.
 */
let windowHandlersInstalled = false;

export function installGlobalErrorHandlers(app: App): void {
  installConsoleRedaction();
  app.config.errorHandler = (error, _instance, info) => {
    logSanitizedError('Vue component failed to render or execute', error, {
      topic1: 'application',
      topic2: 'vue',
      topic3: 'execute',
      context: { vueInfo: info },
    });
  };
  if (typeof window === 'undefined' || windowHandlersInstalled) return;
  windowHandlersInstalled = true;
  window.addEventListener('error', (event) => {
    // Asset names only: no query strings, URL credentials or private path segments.
    const source = event.filename.split('?')[0]?.split('/').pop();
    logSanitizedError(event.message || 'Uncaught browser error', event.error, {
      topic1: 'application',
      topic2: 'window',
      topic3: 'error',
      context: { source, line: event.lineno, column: event.colno },
    });
  });
  window.addEventListener('unhandledrejection', (event) => {
    logSanitizedError('Unhandled promise rejection', event.reason, {
      topic1: 'application',
      topic2: 'window',
      topic3: 'unhandledrejection',
    });
  });
}

function installConsoleRedaction(): void {
  if (consoleRedactionInstalled || typeof console === 'undefined') {
    return;
  }
  consoleRedactionInstalled = true;

  const originalError = console.error.bind(console);
  const originalWarn = console.warn.bind(console);
  const originalLog = console.log.bind(console);
  const sanitizeArgs = (args: unknown[]) => args.map((arg) => sanitizeForLog(arg));

  console.error = (...args: unknown[]) => originalError(...sanitizeArgs(args));
  console.warn = (...args: unknown[]) => originalWarn(...sanitizeArgs(args));
  console.log = (...args: unknown[]) => originalLog(...sanitizeArgs(args));
}
