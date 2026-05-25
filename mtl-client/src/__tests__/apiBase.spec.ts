import { describe, expect, it } from 'vitest';
import { normalizeBackendBaseUrl } from '@/utils/apiBase';

describe('api base URLs', () => {
  it('falls back to the Vite app base so deep routes still call /mtl/api', () => {
    expect(normalizeBackendBaseUrl('', '/mtl/')).toBe('/mtl/');
    expect(normalizeBackendBaseUrl(undefined, '/mtl')).toBe('/mtl/');
    expect(normalizeBackendBaseUrl('./', '/mtl/')).toBe('/mtl/');
    expect(normalizeBackendBaseUrl('.', '/mtl')).toBe('/mtl/');
  });

  it('normalizes explicit relative and absolute backend URLs', () => {
    expect(normalizeBackendBaseUrl('mtl', '/')).toBe('/mtl/');
    expect(normalizeBackendBaseUrl('./api', '/mtl/')).toBe('/mtl/api/');
    expect(normalizeBackendBaseUrl('/custom/mtl', '/')).toBe('/custom/mtl/');
    expect(normalizeBackendBaseUrl('http://localhost:8080/mtl', '/mtl/')).toBe('http://localhost:8080/mtl/');
  });
});
