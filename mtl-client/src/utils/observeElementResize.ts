/** Run layout-changing resize work after the observer's measurement cycle. */
export function observeElementResize(element: Element, onResize: (entry: ResizeObserverEntry) => void): () => void {
  let frame: number | null = null;
  let latestEntry: ResizeObserverEntry | undefined;
  const observer = new ResizeObserver(([entry]) => {
    if (!entry) return;
    latestEntry = entry;
    if (frame !== null) return;
    frame = requestAnimationFrame(() => {
      frame = null;
      if (latestEntry) onResize(latestEntry);
    });
  });
  observer.observe(element);
  return () => {
    observer.disconnect();
    if (frame !== null) cancelAnimationFrame(frame);
    frame = null;
    latestEntry = undefined;
  };
}
