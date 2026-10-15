import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const script = readFileSync('../documentation/tools/website/guide-navigation.js', 'utf8');
const appUrl = 'https://mtl.test/mtl/filter?selection=walking';

function openGuide(opener: unknown = null) {
  const guide = { opener, close: vi.fn() };
  runInNewContext(script, { document, window: guide, URL });
  return guide;
}

function clickBack(options: MouseEventInit = {}) {
  let intercepted = false;
  // Observe the guide handler while preventing jsdom's default navigation.
  document.addEventListener(
    'click',
    (event) => {
      intercepted = event.defaultPrevented;
      event.preventDefault();
    },
    { once: true }
  );
  document
    .querySelector('[data-guide-back]')!
    .dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...options }));
  return intercepted;
}

describe('installed guide return navigation', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <a href="https://mtl.test/mtl/" data-guide-back>Back to app</a>
      <a id="topic" href="https://mtl.test/mtl/guide/planning/index.html">Planning</a>
      <a id="outside" href="https://example.org/">Outside guide</a>`;
  });

  it('focuses the original app and closes the guide without navigating the app', () => {
    const app = { closed: false, location: { href: appUrl }, focus: vi.fn() };
    const guide = openGuide(app);
    expect(clickBack()).toBe(true);
    expect(app.focus).toHaveBeenCalledOnce();
    expect(guide.close).toHaveBeenCalledOnce();
    expect(app.location.href).toBe(appUrl);
  });

  it.each([
    null,
    { closed: true },
    { closed: false, location: { href: 'https://example.org/mtl/' } },
    { closed: false, location: { href: 'https://mtl.test/other/' } },
    { closed: false, location: { href: 'https://mtl.test/mtl/guide/index.html' } },
  ])('keeps the app link fallback for an unavailable app tab: %j', (opener) => {
    const guide = openGuide(opener);
    expect(clickBack()).toBe(false);
    expect(guide.close).not.toHaveBeenCalled();
  });

  it('keeps the fallback if the opener cannot be accessed', () => {
    const guide = openGuide({
      closed: false,
      get location() {
        throw new DOMException('Cross-origin opener', 'SecurityError');
      },
    });
    expect(clickBack()).toBe(false);
    expect(guide.close).not.toHaveBeenCalled();
  });

  it.each(['ctrlKey', 'metaKey', 'shiftKey', 'altKey'])('preserves a %s click', (modifier) => {
    const app = { closed: false, location: { href: appUrl }, focus: vi.fn() };
    const guide = openGuide(app);
    expect(clickBack({ [modifier]: true })).toBe(false);
    expect(app.focus).not.toHaveBeenCalled();
    expect(guide.close).not.toHaveBeenCalled();
  });

  it('opens outside links separately without passing the app connection', () => {
    openGuide();
    const outside = document.querySelector<HTMLAnchorElement>('#outside')!;
    expect(outside.target).toBe('_blank');
    expect(outside.rel).toBe('noopener noreferrer');
    expect(document.querySelector('#topic')!.hasAttribute('target')).toBe(false);
  });
});
