/** Slow startup remains pending; only request failures show an error. */
export const STARTUP_SLOW_NOTICE_MS = 30_000;

/** Allow startup probes to finish while a busy server handles other work. */
export const STARTUP_REQUEST_TIMEOUT_MS = 60_000;
