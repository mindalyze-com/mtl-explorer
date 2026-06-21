const ATTRIBUTION_LINK_SELECTOR = '.maplibregl-ctrl-attrib a[href]';
const EXTERNAL_WINDOW_FEATURES = 'noopener,noreferrer';

interface ConfigureExternalAttributionLinksOptions {
  onBlocked?: (url: string) => void;
  openExternal?: (url: string) => boolean;
}

function externalHttpUrl(value: string): string | null {
  try {
    const url = new URL(value, window.location.href);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

export function openExternalBrowserWindow(url: string): boolean {
  const externalWindow = window.open(url, '_blank', EXTERNAL_WINDOW_FEATURES);
  if (!externalWindow) return false;
  try {
    externalWindow.opener = null;
  } catch {
    // Some PWA/browser surfaces expose a readonly opener. The noopener feature
    // above is the important part; this assignment is a best-effort fallback.
  }
  return true;
}

export function configureExternalAttributionLinks(
  container: HTMLElement,
  options: ConfigureExternalAttributionLinksOptions = {}
): () => void {
  const openExternal = options.openExternal ?? openExternalBrowserWindow;

  for (const link of Array.from(container.querySelectorAll<HTMLAnchorElement>(ATTRIBUTION_LINK_SELECTOR))) {
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
  }

  const onClick = (event: MouseEvent) => {
    const target = event.target instanceof Element ? event.target : null;
    const link = target?.closest<HTMLAnchorElement>(ATTRIBUTION_LINK_SELECTOR) ?? null;
    if (!link || !container.contains(link)) return;

    const url = externalHttpUrl(link.href);
    if (!url) return;

    event.preventDefault();
    event.stopPropagation();
    if (!openExternal(url)) {
      options.onBlocked?.(url);
    }
  };

  container.addEventListener('click', onClick);
  return () => container.removeEventListener('click', onClick);
}
