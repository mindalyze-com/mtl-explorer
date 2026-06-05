import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import TrackReplayControls from '@/components/replay/TrackReplayControls.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: Boolean,
  },
  emits: ['update:modelValue', 'layout-change'],
  template: `
    <div v-if="modelValue">
      <button type="button" data-test="sheet-close" @click="$emit('update:modelValue', false)">Close</button>
      <button
        type="button"
        data-test="sheet-layout"
        @click="$emit('layout-change', {
          open: true,
          detentId: 'open',
          fullscreen: false,
          dragging: false,
          heightPx: 318,
          widthPx: 900,
          topPx: 402,
          rightPx: 900,
          bottomPx: 720,
          leftPx: 0
        })"
      >
        Layout
      </button>
      <slot />
    </div>
  `,
});

const MtlSliderStub = defineComponent({
  name: 'MtlSlider',
  template: '<div data-test="slider" />',
});

function mountControls(props = {}) {
  return mount(TrackReplayControls, {
    props: {
      active: true,
      autoFollow: true,
      cameraPreset: 'follow',
      cameraSmoothness: 50,
      distanceLabel: '0 m / 1 km',
      durationSeconds: 45,
      elapsedLabel: '0m 00s',
      playing: true,
      progress: 0.5,
      showContextTracks: true,
      showTelemetry: true,
      speedFactorLabel: '80x',
      totalLabel: '45s',
      ...props,
    },
    global: {
      stubs: {
        BottomSheet: BottomSheetStub,
        MtlSlider: MtlSliderStub,
      },
    },
  });
}

describe('TrackReplayControls', () => {
  it('keeps sheet close separate from the stop button', async () => {
    const wrapper = mountControls();

    await wrapper.get('button[aria-label="Stop 3D replay"]').trigger('click');
    expect(wrapper.emitted('stop')).toHaveLength(1);
    expect(wrapper.emitted('close')).toBeUndefined();

    await wrapper.get('[data-test="sheet-close"]').trigger('click');
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('emits context track visibility changes', async () => {
    const wrapper = mountControls();

    await wrapper.get('input[aria-label="Show context tracks"]').setValue(false);

    expect(wrapper.emitted('update-show-context-tracks')).toEqual([[false]]);
  });

  it('emits telemetry visibility changes', async () => {
    const wrapper = mountControls();

    await wrapper.get('input[aria-label="Show replay telemetry"]').setValue(false);

    expect(wrapper.emitted('update-show-telemetry')).toEqual([[false]]);
  });

  it('passes bottom-sheet layout changes to the map renderer', async () => {
    const wrapper = mountControls();

    await wrapper.get('[data-test="sheet-layout"]').trigger('click');

    expect(wrapper.emitted('sheet-layout-change')?.[0]?.[0]).toMatchObject({
      open: true,
      heightPx: 318,
      topPx: 402,
    });
  });

  it('labels camera smoothness without percent semantics', () => {
    const wrapper = mountControls({ cameraSmoothness: 100 });

    expect(wrapper.text()).toContain('Cinema');
    expect(wrapper.text()).not.toContain('100%');
  });
});
