import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { computed } from 'vue';
import { mediaContentUrl } from '@/repositories/mediaRepository';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { useApplicationStatusStore } from '@/stores/applicationStatusStore';

describe('media content freshness', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  afterEach(() => {
    setActivePinia(undefined);
    localStorage.clear();
  });

  it('changes reactive media URLs only when the new data revision is applied', () => {
    const store = useDataFreshnessStore();
    const originalUrl = computed(() => mediaContentUrl(7));
    const previewUrl = computed(() => mediaContentUrl(7, 1024));
    store.markAppliedToken('server-a:media=1&tracks=2');
    const originalBefore = originalUrl.value;
    const previewBefore = previewUrl.value;

    useApplicationStatusStore().freshness = { freshnessToken: 'server-a:media=2&tracks=2', items: [] };
    expect(originalUrl.value).toBe(originalBefore);
    expect(previewUrl.value).toBe(previewBefore);

    store.markAppliedToken('server-a:media=2&tracks=2');
    expect(originalUrl.value).not.toBe(originalBefore);
    expect(previewUrl.value).not.toBe(previewBefore);
    const preview = new URL(previewUrl.value, 'http://localhost');
    expect(preview.pathname).toBe('/api/media/get/7/content');
    expect([...preview.searchParams]).toEqual([
      ['maxSize', '1024'],
      ['revision', 'server-a:media=2&tracks=2'],
    ]);
    expect(originalUrl.value).toBe(mediaContentUrl(7));

    store.markAppliedToken('server-a:media=1&tracks=2');
    expect(originalUrl.value).toBe(originalBefore);
    expect(previewUrl.value).toBe(previewBefore);
  });

  it('keeps the applied revision across app reloads and outside a mounted app', () => {
    useDataFreshnessStore().markAppliedToken('persisted-revision');
    const before = mediaContentUrl(7, 320);
    expect(new URL(before, 'http://localhost').searchParams.get('revision')).toBe('persisted-revision');
    setActivePinia(createPinia());
    expect(mediaContentUrl(7, 320)).toBe(before);
    setActivePinia(undefined);
    expect(mediaContentUrl(7, 320)).toBe(before);
  });

  it('keeps content available before the first freshness token arrives', () => {
    expect(mediaContentUrl(7)).toBe('/api/media/get/7/content');
    expect(mediaContentUrl(7, 320)).toBe('/api/media/get/7/content?maxSize=320');
  });
});
