import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const SHEET_WIDTH_DECLARATION =
  /--sheet-(?:inline-gap|desktop-max-width|desktop-wide-max-width|desktop-wide-width)\s*:/;

function vueFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return vueFiles(path);
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : [];
  });
}

describe('bottom sheet width contract', () => {
  it('defines the standard dimensions as global design tokens', () => {
    const baseCss = readFileSync('src/assets/base.css', 'utf8');

    expect(baseCss).toContain('--sheet-inline-gap: 1rem;');
    expect(baseCss).toContain('--sheet-desktop-max-width: 920px;');
    expect(baseCss).toContain('--sheet-desktop-wide-max-width: 1180px;');
    expect(baseCss).toContain('--sheet-desktop-wide-width: 72vw;');
    expect(baseCss).toContain('--sheet-compact-desktop-max-width: 680px;');
    expect(baseCss).toContain('--sheet-compact-desktop-wide-max-width: 720px;');
    expect(baseCss).toContain('--sheet-compact-desktop-wide-width: 54vw;');
  });

  it('keeps every feature sheet on the shared width', () => {
    for (const file of vueFiles('src')) {
      expect(readFileSync(file, 'utf8'), file).not.toMatch(SHEET_WIDTH_DECLARATION);
    }
  });
});
