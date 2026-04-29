const SPLASH_LOGO_TOP_KEY = 'mtl-splash-logo-top';
const MIN_LOGO_TOP_PX = 0;
const MAX_LOGO_TOP_VIEWPORT_RATIO = 0.85;

function getMaxLogoTopPx() {
  if (typeof window === 'undefined') return Number.POSITIVE_INFINITY;
  return window.innerHeight * MAX_LOGO_TOP_VIEWPORT_RATIO;
}

function coerceLogoTop(value: number) {
  if (!Number.isFinite(value)) return null;
  return Math.min(Math.max(value, MIN_LOGO_TOP_PX), getMaxLogoTopPx());
}

export function saveSplashLogoTop(value: number) {
  const logoTop = coerceLogoTop(value);
  if (logoTop === null) return;
  sessionStorage.setItem(SPLASH_LOGO_TOP_KEY, String(Math.round(logoTop)));
}

export function consumeSplashLogoTop() {
  const rawValue = sessionStorage.getItem(SPLASH_LOGO_TOP_KEY);
  sessionStorage.removeItem(SPLASH_LOGO_TOP_KEY);

  if (rawValue === null) return null;
  return coerceLogoTop(Number(rawValue));
}

export function clearSplashLogoTop() {
  sessionStorage.removeItem(SPLASH_LOGO_TOP_KEY);
}
