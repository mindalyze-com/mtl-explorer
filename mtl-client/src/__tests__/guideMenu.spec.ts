import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { beforeEach, describe, expect, it } from 'vitest';

const script = readFileSync('../documentation/tools/website/documentation.js', 'utf8');

function openGuide(desktop: boolean) {
  const breakpoint = new EventTarget() as EventTarget & { matches: boolean };
  breakpoint.matches = desktop;
  runInNewContext(script, { document, window: { matchMedia: () => breakpoint } });
  return {
    menu: document.querySelector<HTMLDetailsElement>('#doc-menu')!,
    resize(desktop: boolean) {
      breakpoint.matches = desktop;
      breakpoint.dispatchEvent(Object.assign(new Event('change'), { matches: desktop }));
    },
  };
}

describe('responsive guide menu', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <details id="doc-menu" open><summary>Browse guide</summary><a href="#content">Article</a></details>
      <details class="doc-contents" open><summary>On this page</summary><a href="#section">Section</a></details>`;
  });

  it('starts open on desktop and collapsed on mobile', () => {
    expect(openGuide(true).menu.open).toBe(true);
    expect(openGuide(false).menu.open).toBe(false);
  });

  it('updates when crossing the desktop breakpoint', () => {
    const guide = openGuide(false);
    guide.resize(true);
    expect(guide.menu.open).toBe(true);
    guide.resize(false);
    expect(guide.menu.open).toBe(false);
  });

  it('keeps the article outline collapsed on mobile and opens it on desktop', () => {
    const guide = openGuide(false);
    const contents = document.querySelector<HTMLDetailsElement>('.doc-contents')!;
    expect(contents.open).toBe(false);
    guide.resize(true);
    expect(contents.open).toBe(true);
  });

  it('closes the mobile menu with Escape and returns focus to its control', () => {
    const guide = openGuide(false);
    guide.menu.open = true;
    guide.menu.querySelector('a')!.focus();
    guide.menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(guide.menu.open).toBe(false);
    expect(document.activeElement).toBe(guide.menu.querySelector('summary'));
  });

  it('keeps desktop navigation open when Escape is pressed', () => {
    const guide = openGuide(true);
    guide.menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(guide.menu.open).toBe(true);
  });
});
