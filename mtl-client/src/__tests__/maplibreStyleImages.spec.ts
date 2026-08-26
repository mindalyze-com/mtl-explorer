import { describe, expect, it, vi } from 'vitest';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';

describe('MapLibre missing style images', () => {
  it('uses the MapLibre 6 resolver and adds one transparent pixel for an unresolved image', () => {
    let resolver: ((id: string) => void) | undefined;
    const map = {
      setMissingStyleImageResolver: vi.fn((nextResolver: (id: string) => void) => {
        resolver = nextResolver;
      }),
      hasImage: vi.fn(() => false),
      addImage: vi.fn(),
    };

    installMissingStyleImageResolver(map as never);
    resolver?.('optional-icon');

    expect(map.setMissingStyleImageResolver).toHaveBeenCalledOnce();
    expect(map.addImage).toHaveBeenCalledWith('optional-icon', {
      width: 1,
      height: 1,
      data: new Uint8ClampedArray(4),
    });
  });

  it('leaves images that are already available unchanged', () => {
    let resolver: ((id: string) => void) | undefined;
    const map = {
      setMissingStyleImageResolver: (nextResolver: (id: string) => void) => {
        resolver = nextResolver;
      },
      hasImage: vi.fn(() => true),
      addImage: vi.fn(),
    };

    installMissingStyleImageResolver(map as never);
    resolver?.('available-icon');

    expect(map.addImage).not.toHaveBeenCalled();
  });
});
