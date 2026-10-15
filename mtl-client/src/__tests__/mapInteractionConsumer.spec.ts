import { mount } from '@vue/test-utils';
import { defineComponent, h, provide } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import {
  MAP_INTERACTION_REGISTRATION_KEY,
  useMapInteractionConsumer,
} from '@/components/map/composables/useMapInteractionConsumer';
import type { MapInteractionConsumer } from '@/components/map/mapInteractionCoordinator';

describe('map child consumer lifecycle', () => {
  it('registers through its parent dispatcher and unregisters on unmount', () => {
    const cleanup = vi.fn();
    const register = vi.fn(() => cleanup);
    const collect = vi.fn(() => []);
    const consumer: MapInteractionConsumer = { id: 'child-places', kind: 'local', collect };
    const Child = defineComponent({
      setup() {
        useMapInteractionConsumer(consumer);
        return () => h('span', 'Places');
      },
    });
    const Parent = defineComponent({
      setup() {
        provide(MAP_INTERACTION_REGISTRATION_KEY, register);
        return () => h(Child);
      },
    });
    const wrapper = mount(Parent);
    expect(register).toHaveBeenCalledExactlyOnceWith(consumer);
    expect(cleanup).not.toHaveBeenCalled();
    wrapper.unmount();
    expect(cleanup).toHaveBeenCalledOnce();
  });
});
