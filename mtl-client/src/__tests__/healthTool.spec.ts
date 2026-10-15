import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HealthTool from '@/features/health/HealthTool.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import SectionHub from '@/components/ui/SectionHub.vue';
import HealthOverviewScreen from '@/features/health/screens/overview/HealthOverviewScreen.vue';
import HealthRawDataScreen from '@/features/health/screens/raw/HealthRawDataScreen.vue';

const demoStatusMock = vi.hoisted(() => vi.fn());
const replaceRouteMock = vi.hoisted(() => vi.fn());

vi.mock('@/utils/serverAdminApi', () => ({ getDemoStatus: demoStatusMock }));
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ name: 'health' }),
  useRouter: () => ({ replace: replaceRouteMock }),
}));

enableAutoUnmount(afterEach);

async function openHealthTool() {
  const wrapper = shallowMount(HealthTool, {
    global: {
      stubs: {
        SectionHub: false,
        BottomSheet: {
          props: ['modelValue', 'backLabel', 'contentKey'],
          emits: ['back', 'closed'],
          template:
            '<div v-if="modelValue"><slot name="title" /><button v-if="backLabel" class="back" @click="$emit(\'back\')">{{ backLabel }}</button><slot /></div>',
        },
      },
    },
  });
  wrapper.vm.open();
  await flushPromises();
  return wrapper;
}

describe('HealthTool navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    demoStatusMock.mockResolvedValue({ demoMode: false });
    replaceRouteMock.mockResolvedValue(undefined);
  });

  it('opens on a Health hub and dispatches to focused views with a Back action', async () => {
    const wrapper = await openHealthTool();
    const hub = wrapper.getComponent(SectionHub);

    expect(wrapper.get('[data-test="health-hub"]').exists()).toBe(true);
    expect(wrapper.get('.panel-title').text()).toBe('Google Health (beta)');
    expect(hub.props('title')).toBe('Explore your health data');
    expect(hub.props('items')).toEqual([
      expect.objectContaining({ value: 'overview', label: 'Overview' }),
      expect.objectContaining({ value: 'data', label: 'Data & Records' }),
    ]);

    hub.vm.$emit('open', 'data');
    await flushPromises();

    expect(wrapper.get('.panel-title').text()).toBe('Data & Records (beta)');
    expect(wrapper.getComponent(BottomSheet).props('backLabel')).toBe('Back to Health');
    expect(wrapper.findComponent(HealthRawDataScreen).exists()).toBe(true);
    expect(wrapper.findComponent(HealthOverviewScreen).exists()).toBe(false);

    await wrapper.get('.back').trigger('click');

    expect(wrapper.get('[data-test="health-hub"]').exists()).toBe(true);
    expect(wrapper.getComponent(BottomSheet).props('backLabel')).toBe('');
    expect(wrapper.findComponent(HealthRawDataScreen).exists()).toBe(false);
  });

  it('keeps the overview data-details action inside the Health hub flow', async () => {
    const wrapper = await openHealthTool();
    wrapper.getComponent(SectionHub).vm.$emit('open', 'overview');
    await flushPromises();

    wrapper.getComponent(HealthOverviewScreen).vm.$emit('open-data-details');
    await flushPromises();

    expect(wrapper.get('.panel-title').text()).toBe('Data & Records (beta)');
    expect(wrapper.findComponent(HealthRawDataScreen).exists()).toBe(true);
  });

  it('reopens on the hub without a last-viewed shortcut', async () => {
    const wrapper = await openHealthTool();
    wrapper.getComponent(SectionHub).vm.$emit('open', 'data');
    await flushPromises();

    wrapper.getComponent(BottomSheet).vm.$emit('closed');
    await flushPromises();
    expect(replaceRouteMock).toHaveBeenCalledWith({ name: 'home' });

    wrapper.vm.open();
    await flushPromises();

    expect(wrapper.get('[data-test="health-hub"]').exists()).toBe(true);
    expect(wrapper.find('.section-hub__footer').exists()).toBe(false);
    expect(wrapper.findComponent(HealthOverviewScreen).exists()).toBe(false);
  });

  it('keeps only the overview dispatcher in demo mode', async () => {
    demoStatusMock.mockResolvedValue({ demoMode: true });
    const wrapper = await openHealthTool();

    expect(wrapper.getComponent(SectionHub).props('items')).toEqual([
      expect.objectContaining({ value: 'overview', label: 'Overview' }),
    ]);
    wrapper.getComponent(SectionHub).vm.$emit('open', 'overview');
    await flushPromises();

    expect(wrapper.getComponent(HealthOverviewScreen).props('demoMode')).toBe(true);
    expect(wrapper.findComponent(HealthRawDataScreen).exists()).toBe(false);
  });

  it('opens both Health views when synthetic demo data is enabled', async () => {
    demoStatusMock.mockResolvedValue({ demoMode: true, syntheticHealthData: true });
    const wrapper = await openHealthTool();
    const hub = wrapper.getComponent(SectionHub);
    expect(hub.props('items')).toHaveLength(2);
    expect(hub.props('description')).toContain('synthetic');
    hub.vm.$emit('open', 'overview');
    await flushPromises();
    expect(wrapper.getComponent(HealthOverviewScreen).props('syntheticData')).toBe(true);
    wrapper.getComponent(HealthOverviewScreen).vm.$emit('open-data-details');
    await flushPromises();
    expect(wrapper.getComponent(HealthRawDataScreen).props('syntheticData')).toBe(true);
    expect(wrapper.vm.getNavigationState()).toEqual({ screen: 'data' });
  });

  it('restores a focused Health view after returning from another route', async () => {
    const wrapper = await openHealthTool();
    wrapper.vm.restoreNavigationState({ screen: 'data' });
    await flushPromises();

    expect(wrapper.get('.panel-title').text()).toBe('Data & Records (beta)');
    expect(wrapper.vm.getNavigationState()).toEqual({ screen: 'data' });
  });
});
