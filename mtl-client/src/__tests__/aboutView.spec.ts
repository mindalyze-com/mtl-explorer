import { shallowMount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import AboutView from '@/views/AboutView.vue';
import { isAuthenticated } from '@/utils/auth';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';

const router = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRouter: () => router,
}));

vi.mock('@/utils/auth', () => ({
  isAuthenticated: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  getDemoStatus: vi.fn(async () => ({ demoMode: false })),
}));

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: Boolean,
    title: String,
    sheetClass: String,
    noScrollHint: Boolean,
    viewportCentered: Boolean,
  },
  emits: ['closed', 'update:modelValue'],
  template:
    '<div class="bottom-sheet-stub"><button class="bottom-sheet-close-stub" @click="$emit(\'closed\')">Close</button><slot /></div>',
});

function mountAbout(embedded = false, viewportCentered = true) {
  return shallowMount(AboutView, {
    props: { embedded, viewportCentered },
    global: {
      stubs: {
        BottomSheet: BottomSheetStub,
        BuildVersionInfo: false,
      },
    },
  });
}

describe('AboutView navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.stubGlobal('__APP_PKG_VERSION__', '1.0.0');
    vi.stubGlobal('__APP_VERSION__', '2026-08-20T06:00:00.000Z');
    window.history.replaceState(null, '');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    window.history.replaceState(null, '');
  });

  it('keeps the About content inside the standard bottom sheet', () => {
    const wrapper = mountAbout();

    expect(wrapper.getComponent(BottomSheetStub).props('title')).toBe('About MTL Explorer');
    expect(wrapper.getComponent(BottomSheetStub).props('sheetClass')).toContain('sheet--about');
    expect(wrapper.getComponent(BottomSheetStub).props('noScrollHint')).toBe(false);
    expect(wrapper.getComponent(BottomSheetStub).props('viewportCentered')).toBe(true);
    expect(wrapper.get('h1').text()).toBe('MTL Explorer');
    expect(wrapper.get('.about-build').text()).toContain(
      `Client image versionDevelopmentClient built${formatDateAndTimeWithSeconds('2026-08-20T06:00:00.000Z')}`
    );
    expect(wrapper.get('.about-build').text()).not.toContain('1.0.0');
    expect(wrapper.get('.about-build').text()).not.toContain('Deployment release');
    expect(wrapper.get('.about-overview__source').attributes('href')).toBe(
      'https://github.com/mindalyze-com/mtl-explorer'
    );
    expect(wrapper.get('a[href$="/guide/index.html"]').attributes('rel')).toBe('opener');
    expect(wrapper.get('.about-overview__source').attributes('rel')).toBe('noopener noreferrer');
    expect(wrapper.text()).not.toContain('Network use is distribution.');
    expect(wrapper.text()).toContain('commercial license');
    expect(wrapper.text()).toContain('not a safety-critical navigation system');
  });

  it('can use the shared navigation-aware centering when embedded in Admin', () => {
    const wrapper = mountAbout(true, false);

    expect(wrapper.getComponent(BottomSheetStub).props('viewportCentered')).toBe(false);
  });

  it('leads signed-in users to all component versions without individual copy actions', async () => {
    vi.mocked(isAuthenticated).mockReturnValue(true);
    const wrapper = mountAbout();
    const action = wrapper.findAll('button').find((button) => button.text() === 'All component versions')!;
    await action.trigger('click');
    expect(router.push).toHaveBeenCalledWith({ name: 'admin', params: { section: 'system' } });
    expect(wrapper.find('[aria-label="Copy version and full Git revision"]').exists()).toBe(false);
    const embedded = mountAbout(true);
    await embedded
      .findAll('button')
      .find((button) => button.text() === 'All component versions')!
      .trigger('click');
    expect(embedded.emitted('show-system-information')).toEqual([[]]);
    vi.mocked(isAuthenticated).mockReturnValue(false);
  });

  it('shows the release image identity when the build provides one', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.405');
    vi.stubEnv('VITE_APP_BUILD', '2026-08-20T06:15:00Z');
    vi.stubEnv('VITE_APP_REVISION', '48a4874aa741dc980574531172b98f11c91d6017');

    const wrapper = mountAbout();

    expect(wrapper.get('.about-build').text()).toContain('Client image version1.405');
    expect(wrapper.get('.about-build').text()).toContain(
      `Client built${formatDateAndTimeWithSeconds('2026-08-20T06:00:00.000Z')}`
    );
    expect(wrapper.get('.about-build').text()).not.toContain('2026-08-20T06:15:00Z');
    expect(wrapper.get('.build-version__summary').text()).toBe('1.405·48a4874a');
    expect(wrapper.find('.build-version__details').exists()).toBe(false);
  });

  it('shows the catalog deployment release separately from the unchanged image build', async () => {
    vi.stubEnv('VITE_APP_VERSION', '1.405');
    const updates = useReleaseUpdatesStore();
    const wrapper = mountAbout();
    expect(wrapper.get('.about-build').text()).not.toContain('Deployment release');

    updates.status = { installedReleaseVersion: '0.9.0', installedBuildId: '1.405' };
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.about-build').text()).toContain('Deployment release0.9.0');
    expect(wrapper.get('.build-version__summary').text()).toBe('1.405');

    updates.status = { installedReleaseVersion: '0.9.1', installedBuildId: '1.405' };
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.about-build').text()).toContain('Deployment release0.9.1');
    expect(wrapper.get('.build-version__summary').text()).toBe('1.405');
  });

  it('shows the version alone when the build did not provide a revision', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.405');
    vi.stubEnv('VITE_APP_REVISION', '');
    const wrapper = mountAbout();
    expect(wrapper.get('.build-version__summary').text()).toBe('1.405');
    expect(wrapper.find('[aria-controls]').exists()).toBe(false);
  });

  it('keeps reference material in closed disclosures by default', () => {
    const wrapper = mountAbout();
    const disclosures = wrapper.findAll('.about-disclosure');

    expect(disclosures).toHaveLength(5);
    expect(disclosures.every((disclosure) => disclosure.attributes('open') === undefined)).toBe(true);
    expect(wrapper.get('[data-test="about-license"] > summary').text()).toContain('License and commercial use');
    expect(wrapper.get('[data-test="about-credits"] > summary').text()).toContain('Credits and data sources');
    expect(wrapper.get('[data-test="about-contribute"] > summary').text()).toContain('Contribute');
    expect(wrapper.get('[data-test="about-trademark"] > summary').text()).toContain('Trademark');
    expect(wrapper.get('[data-test="about-disclaimer"] > summary').text()).toContain('Safety disclaimer');
  });

  it('closes as an embedded sheet without changing the route', async () => {
    const wrapper = mountAbout(true);

    expect(wrapper.classes()).toContain('about-embedded');
    expect(wrapper.find('.about-route__ambient').exists()).toBe(false);

    await wrapper.get('.bottom-sheet-close-stub').trigger('click');

    expect(wrapper.emitted('closed')).toEqual([[]]);
    expect(router.back).not.toHaveBeenCalled();
    expect(router.push).not.toHaveBeenCalled();
  });

  it('returns to the previous application route when one is recorded', async () => {
    window.history.replaceState({ back: '/admin/session' }, '');
    const wrapper = mountAbout();

    await wrapper.get('.bottom-sheet-close-stub').trigger('click');

    expect(router.back).toHaveBeenCalledOnce();
    expect(router.push).not.toHaveBeenCalled();
  });

  it('opens the map for an authenticated direct visit', async () => {
    vi.mocked(isAuthenticated).mockReturnValue(true);
    const wrapper = mountAbout();

    await wrapper.get('.bottom-sheet-close-stub').trigger('click');

    expect(router.push).toHaveBeenCalledWith({ name: 'home' });
  });

  it('opens login for an unauthenticated direct visit', async () => {
    vi.mocked(isAuthenticated).mockReturnValue(false);
    const wrapper = mountAbout();

    await wrapper.get('.bottom-sheet-close-stub').trigger('click');

    expect(router.push).toHaveBeenCalledWith({ name: 'login' });
  });
});
