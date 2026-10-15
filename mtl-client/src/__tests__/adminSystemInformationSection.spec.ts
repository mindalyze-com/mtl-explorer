import { shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminSystemInformationSection from '@/components/admin/AdminSystemInformationSection.vue';
import AllVersionsReport from '@/components/admin/AllVersionsReport.vue';
import BuildVersionInfo from '@/components/ui/BuildVersionInfo.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';

vi.mock('@/utils/Utils', () => ({
  formatDateAndTimeWithSeconds: (value?: string) => value ?? '',
}));

const revision = '48a4874aa741dc980574531172b98f11c91d6017';

function mountInformation(
  image: { version: string; revision?: string; buildTime?: string },
  build: { buildTime?: string } = {}
) {
  vi.stubGlobal('matchMedia', () => ({ matches: false }));
  return shallowMount(AdminSystemInformationSection, {
    props: {
      serverBuild: { ...build, image },
      operationalTasks: [],
      loading: false,
      error: '',
    },
    global: { stubs: { BuildVersionInfo: false, Button: { template: '<button />' } } },
  });
}

describe('system information image revision', () => {
  beforeEach(() => setActivePinia(createPinia()));
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('combines the server image version and short revision with access to the full revision', () => {
    const wrapper = mountInformation({ version: '1.501', revision });
    expect(wrapper.get('[data-test="system-info-server-image-version"] .build-version__summary').text()).toBe(
      '1.501·48a4874a'
    );
    expect(wrapper.find('.build-version__details').exists()).toBe(false);
    expect(wrapper.getComponent(BuildVersionInfo).props('revision')).toBe(revision);
  });

  it('does not invent a revision for older images', () => {
    const wrapper = mountInformation({ version: '1.414' });
    expect(wrapper.get('[data-test="system-info-server-image-version"] .build-version__summary').text()).toBe('1.414');
    expect(wrapper.find('[aria-label="Show full Git revision"]').exists()).toBe(false);
  });

  it('excludes stale server and service versions after refresh failures', async () => {
    const wrapper = mountInformation({ version: '1.501', revision });
    await wrapper.setProps({
      error: 'Private connection failure',
      servicesUnavailable: true,
      services: { planner: { versionInfo: { image: { version: 'stale-routing' } } } },
      operationalTasks: [{ id: 'routing-segments', versionInfo: { image: { version: 'stale-routing' } } }],
    });
    const report = wrapper.getComponent(AllVersionsReport).props('report');
    expect(report).toContain('Application server\nVersion: Unavailable');
    expect(report).not.toContain('1.501');
    expect(report).not.toContain('stale-routing');
    expect(report).not.toContain('Private');
  });

  it('reports available versions even when a component is hidden from operational tasks', async () => {
    const wrapper = mountInformation({ version: '1.501', revision });
    await wrapper.setProps({ services: { tileMode: 'remote', map: { versionInfo: { image: { version: '1.210' } } } } });
    expect(wrapper.getComponent(AllVersionsReport).props('report')).toContain('Map service\nImage version: 1.210');
  });
});

describe('system information client image status', () => {
  beforeEach(() => setActivePinia(createPinia()));
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('lists both builds in the same order and hides package versions', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.501');
    vi.stubEnv('VITE_APP_REVISION', revision);
    const wrapper = mountInformation({ version: '1.501', revision });
    const cardLabels = (heading: string) =>
      wrapper
        .get(`[aria-labelledby="${heading}"]`)
        .findAll('dt')
        .map((term) => term.text());
    expect(cardLabels('admin-server-build-heading')).toEqual(['Image version', 'Built', 'Server ID']);
    expect(cardLabels('admin-client-build-heading')).toEqual([
      'Image version',
      'Built',
      'Status',
      'Running as',
      'ClientLoginRef',
    ]);
    expect(wrapper.get('[data-test="system-info-client-image-version"] .build-version__summary').text()).toBe(
      '1.501·48a4874a'
    );
  });

  it('reports an up-to-date client when image version and revision match', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.501');
    vi.stubEnv('VITE_APP_REVISION', revision);
    const wrapper = mountInformation({ version: '1.501', revision });
    expect(wrapper.get('[data-test="system-info-client-status"]').text()).toContain('Up to date');
    expect(wrapper.find('[data-test="system-info-client-reload"]').exists()).toBe(false);
  });

  it('flags a client built from an older image', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.500');
    const wrapper = mountInformation({ version: '1.501', revision });
    expect(wrapper.get('[data-test="system-info-client-status"]').text()).toContain('Outdated, server runs 1.501');
    expect(wrapper.find('[data-test="system-info-client-reload"]').exists()).toBe(true);
  });

  it('flags a rebuilt image that kept its version tag', () => {
    vi.stubEnv('VITE_APP_VERSION', '1.501');
    vi.stubEnv('VITE_APP_REVISION', 'c0ffee0aa741dc980574531172b98f11c91d6017');
    const wrapper = mountInformation({ version: '1.501', revision });
    expect(wrapper.get('[data-test="system-info-client-status"]').text()).toContain('Outdated');
  });

  it('shows no status for development builds or when the server build is unavailable', async () => {
    const development = mountInformation({ version: '1.501', revision });
    expect(development.find('[data-test="system-info-client-status"]').exists()).toBe(false);

    vi.stubEnv('VITE_APP_VERSION', '1.500');
    const failed = mountInformation({ version: '1.501', revision });
    await failed.setProps({ error: 'Build information unavailable' });
    expect(failed.find('[data-test="system-info-client-status"]').exists()).toBe(false);
  });

  it('activates a waiting service worker before reloading when one is available', async () => {
    vi.stubEnv('VITE_APP_VERSION', '1.500');
    const reloadBrowser = vi.fn();
    const updates = useReleaseUpdatesStore();
    updates.setBrowserUpdate(true, reloadBrowser);
    const wrapper = mountInformation({ version: '1.501', revision });
    await wrapper.get('[data-test="system-info-client-reload"]').trigger('click');
    expect(reloadBrowser).toHaveBeenCalledTimes(1);
  });

  it('reloads the page when no service worker update is waiting', async () => {
    vi.stubEnv('VITE_APP_VERSION', '1.500');
    const reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });
    const wrapper = mountInformation({ version: '1.501', revision });
    await wrapper.get('[data-test="system-info-client-reload"]').trigger('click');
    expect(reload).toHaveBeenCalledTimes(1);
  });
});

describe('system information build times', () => {
  beforeEach(() => setActivePinia(createPinia()));
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('shows the server application build time', () => {
    const wrapper = mountInformation(
      { version: '1.501', buildTime: '2026-10-15T13:00:06Z' },
      { buildTime: '2026-10-15T13:00:24Z' }
    );
    expect(wrapper.get('[data-test="system-info-server-built"] dd').text()).toBe('2026-10-15T13:00:24Z');
  });

  it('falls back to the image build time for servers without build information', () => {
    const wrapper = mountInformation({ version: '1.414', buildTime: '2026-10-15T13:00:06Z' });
    expect(wrapper.get('[data-test="system-info-server-built"] dd').text()).toBe('2026-10-15T13:00:06Z');
  });

  it('shows the client frontend build time', () => {
    vi.stubGlobal('__APP_VERSION__', '2026-10-15T13:01:16.221Z');
    const wrapper = mountInformation({ version: '1.501' });
    expect(wrapper.get('[data-test="system-info-client-built"] dd').text()).toBe('2026-10-15T13:01:16.221Z');
  });
});
