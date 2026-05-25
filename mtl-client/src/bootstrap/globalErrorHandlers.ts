import type { App } from 'vue';
import { describeError } from '@/utils/startupDiagnostics';
import { sanitizeForLog } from '@/utils/safeLogging';

let consoleRedactionInstalled = false;

/**
 * Centralizes Vue's component error handler plus window-level uncaught error
 * and unhandled-rejection listeners. Extracted from main.ts.
 *
 * Catches silent failures that never reach component code so they show up in
 * the console with a recognizable [MTL] prefix.
 */
export function installGlobalErrorHandlers(app: App): void {
  installConsoleRedaction();

  app.config.errorHandler = (err, _instance, info) => {
    console.error('🚨 [Vue Error] Component failed to render or execute:', sanitizeForLog(err), info);
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
        sanitizeForLog(event.error)
      );
    });
    window.addEventListener('unhandledrejection', (event) => {
      console.error('[MTL] Unhandled promise rejection:', describeError(event.reason));
    });
  }
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
