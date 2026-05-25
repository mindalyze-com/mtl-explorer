import { describe, expect, it } from 'vitest';
import hljs from 'highlight.js/lib/core';
import { MTL_SQL_HIGHLIGHT_LANGUAGE, registerMtlSqlHighlight } from '@/utils/mtlSqlHighlight';

describe('MTL SQL highlighting', () => {
  it('highlights named params without highlighting quoted or commented params', () => {
    registerMtlSqlHighlight(hljs);

    const html = hljs.highlight(
      `SELECT id
FROM gps_track
WHERE id = :TRACK_ID
  AND track_name ILIKE '%' || BTRIM(:SEARCH_WORD) || '%'
  AND track_id = ANY(:TRACK_IDS::bigint[])
  AND ST_DWithin(geom::geography, :GEO_CIRCLE_1_POINT::geography, :GEO_CIRCLE_1_RADIUS)
  AND literal = ':DO_NOT_HIGHLIGHT'
  -- comment with :COMMENTED_PARAM`,
      { language: MTL_SQL_HIGHLIGHT_LANGUAGE }
    ).value;

    expect(html).toContain('<span class="hljs-mtl-param">:TRACK_ID</span>');
    expect(html).toContain('<span class="hljs-mtl-param">:SEARCH_WORD</span>');
    expect(html).toContain('<span class="hljs-mtl-param">:TRACK_IDS</span>');
    expect(html).toContain('<span class="hljs-mtl-param">:GEO_CIRCLE_1_POINT</span>');
    expect(html).toContain('<span class="hljs-mtl-param">:GEO_CIRCLE_1_RADIUS</span>');
    expect(html).not.toContain('<span class="hljs-mtl-param">:bigint</span>');
    expect(html).not.toContain('<span class="hljs-mtl-param">:geography</span>');
    expect(html).not.toContain('<span class="hljs-mtl-param">:DO_NOT_HIGHLIGHT</span>');
    expect(html).not.toContain('<span class="hljs-mtl-param">:COMMENTED_PARAM</span>');
  });
});
