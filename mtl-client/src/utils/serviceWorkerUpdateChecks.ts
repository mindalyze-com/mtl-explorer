export const SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000;
export const SERVICE_WORKER_UPDATE_CHECK_COOLDOWN_MS = 30 * 1000;

interface ServiceWorkerUpdateCheckOptions {
  checkIntervalMs?: number;
  cooldownMs?: number;
  now?: () => number;
  targetDocument?: Document;
  targetWindow?: Window;
}

/**
 * Checks for a new service worker now, while the app is open, and whenever it
 * returns to the foreground or regains network access.
 */
export function startServiceWorkerUpdateChecks(
  registration: Pick<ServiceWorkerRegistration, 'update'>,
  options: ServiceWorkerUpdateCheckOptions = {}
): () => void {
  const {
    checkIntervalMs = SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS,
    cooldownMs = SERVICE_WORKER_UPDATE_CHECK_COOLDOWN_MS,
    now = Date.now,
    targetDocument = document,
    targetWindow = window,
  } = options;

  let lastCheckStartedAt = Number.NEGATIVE_INFINITY;
  let updateInFlight = false;

  const checkForUpdate = async (ignoreCooldown = false) => {
    const checkStartedAt = now();
    if (
      updateInFlight ||
      !targetWindow.navigator.onLine ||
      (!ignoreCooldown && checkStartedAt - lastCheckStartedAt < cooldownMs)
    ) {
      return;
    }

    lastCheckStartedAt = checkStartedAt;
    updateInFlight = true;
    try {
      await registration.update();
    } catch (error) {
      console.warn('[PWA] Service worker update check failed', error);
    } finally {
      updateInFlight = false;
    }
  };

  const checkWhenVisible = () => {
    if (targetDocument.visibilityState === 'visible') {
      void checkForUpdate();
    }
  };
  const checkWhenOnline = () => void checkForUpdate(true);

  void checkForUpdate(true);
  const intervalId = targetWindow.setInterval(() => void checkForUpdate(), checkIntervalMs);
  targetDocument.addEventListener('visibilitychange', checkWhenVisible);
  targetWindow.addEventListener('focus', checkWhenVisible);
  targetWindow.addEventListener('online', checkWhenOnline);

  return () => {
    targetWindow.clearInterval(intervalId);
    targetDocument.removeEventListener('visibilitychange', checkWhenVisible);
    targetWindow.removeEventListener('focus', checkWhenVisible);
    targetWindow.removeEventListener('online', checkWhenOnline);
  };
}
