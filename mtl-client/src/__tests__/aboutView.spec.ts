import { shallowMount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import AboutView from '@/views/AboutView.vue';
import { isAuthenticated } from '@/utils/auth';

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
      },
    },
  });
}

describe('AboutView navigation', () => {
  beforeEach(() => {
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
    expect(wrapper.get('.about-build').text()).toContain('Version');
    expect(wrapper.get('.about-build').text()).toContain('Build');
    expect(wrapper.get('.about-build').text()).toContain('1.0.0');
    expect(wrapper.get('.about-build').text()).not.toContain('Versiondev');
    expect(wrapper.get('.about-build').text()).not.toContain('Buildlocal build');
    expect(wrapper.get('.about-overview__source').attributes('href')).toBe(
      'https://github.com/mindalyze-com/mtl-explorer'
    );
    expect(wrapper.text()).not.toContain('Network use is distribution.');
    expect(wrapper.text()).toContain('commercial license');
    expect(wrapper.text()).toContain('not a safety-critical navigation system');
  });

  it('can use the shared navigation-aware centering when embedded in Admin', () => {
    const wrapper = mountAbout(true, false);

    expect(wrapper.getComponent(BottomSheetStub).props('viewportCentered')).toBe(false);
  });

  it('shows the release image identity when the build provides one', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.405');
    vi.stubEnv('VITE_APP_BUILD', '2026-08-20T06:15:00Z');

    const wrapper = mountAbout();

    expect(wrapper.get('.about-build').text()).toContain('Version1.405');
    expect(wrapper.get('.about-build').text()).toContain('Build2026-08-20T06:15:00Z');
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
