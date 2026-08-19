import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('main map media viewer title', () => {
  it('keeps the chosen map photo scope visible', () => {
    const source = readFileSync('src/components/map/Map2DRenderer.vue', 'utf8');
    const viewerSection = source.match(
      /<!-- ─── Media photo bottom sheet ─── -->[\s\S]*?<!-- ─── Track selection bottom sheet ─── -->/
    )?.[0];

    expect(viewerSection).toBeDefined();
    expect(viewerSection).toContain(':title="mediaViewerScopeLabel"');
    expect(viewerSection).toContain('<span>{{ mediaViewerScopeLabel }}</span>');
    expect(viewerSection).toContain(':collection-label="mediaViewerFilmstripLabel"');
    expect(viewerSection).not.toContain('Activity photo');
  });
});
