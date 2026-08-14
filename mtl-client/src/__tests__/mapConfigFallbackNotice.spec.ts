import { readFileSync } from 'node:fs';
import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import MapConfigFallbackNotice from '@/components/map/MapConfigFallbackNotice.vue';

describe('MapConfigFallbackNotice', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('explains the fallback and offers retry and dismiss actions', async () => {
    const wrapper = mount(MapConfigFallbackNotice, { attachTo: document.body });
    const notice = document.body.querySelector('[data-test="map-config-fallback-notice"]');

    expect(notice?.textContent).toContain('Map settings could not be loaded');
    expect(notice?.textContent).toContain('A basic online map is being used.');

    const buttons = [...document.body.querySelectorAll('button')];
    buttons[0]?.click();
    buttons[1]?.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('retry')).toHaveLength(1);
    expect(wrapper.emitted('dismiss')).toHaveLength(1);
  });

  it('prevents duplicate actions while retrying', () => {
    mount(MapConfigFallbackNotice, { props: { retrying: true }, attachTo: document.body });

    expect(document.body.textContent).toContain('Retrying…');
    expect([...document.body.querySelectorAll('button')].every((button) => button.disabled)).toBe(true);
  });

  it('uses an opaque theme surface instead of an undefined transparent background', () => {
    const source = readFileSync('src/components/map/MapConfigFallbackNotice.vue', 'utf8');

    expect(source.match(/background: var\(--surface-sheet-solid\);/g)).toHaveLength(2);
    expect(source).not.toContain('var(--surface-card)');
  });
});
