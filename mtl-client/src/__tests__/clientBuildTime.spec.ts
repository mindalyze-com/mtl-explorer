import { shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AboutView from '@/views/AboutView.vue';
import AdminSystemInformationSection from '@/components/admin/AdminSystemInformationSection.vue';
import AllVersionsReport from '@/components/admin/AllVersionsReport.vue';

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }));
vi.mock('@/utils/auth', () => ({ isAuthenticated: () => false, getClientLoginRef: () => null }));
vi.mock('@/utils/ServiceHelper', () => ({ getDemoStatus: async () => ({ demoMode: false }) }));
vi.mock('@/utils/Utils', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/Utils')>()),
  formatDateAndTimeWithSeconds: (value?: string | null) => (value ? `formatted ${value}` : ''),
}));

const IMAGE_BUILD_TIME = '2026-10-06T17:45:13Z';
const CLIENT_BUILD_TIME = '2026-10-06T17:46:11.000Z';
const SERVER_BUILD_TIME = '2026-10-06T17:45:40Z';

function mountBuildViews() {
  const about = shallowMount(AboutView, {
    global: { stubs: { BottomSheet: { template: '<section><slot /></section>' }, BuildVersionInfo: false } },
  });
  const system = shallowMount(AdminSystemInformationSection, {
    props: {
      serverBuild: { buildTime: SERVER_BUILD_TIME, image: { version: '1.522', buildTime: IMAGE_BUILD_TIME } },
      operationalTasks: [],
      loading: false,
      error: '',
    },
    global: { stubs: { BuildVersionInfo: false } },
  });
  const aboutRow = (label: string) =>
    about
      .findAll('.about-build > div')
      .find((row) => row.get('dt').text() === label)!
      .get('dd')
      .text();
  const clientReport = system.getComponent(AllVersionsReport).props('report').split('Application server')[0];
  return {
    about,
    system,
    aboutVersion: aboutRow('Client image version'),
    aboutTime: aboutRow('Client built'),
    systemVersion: system.get('[data-test="system-info-client-image-version"] dd').text(),
    systemTime: system.get('[data-test="system-info-client-built"] dd').text(),
    serverTime: system.get('[data-test="system-info-server-built"] dd').text(),
    clientReport,
  };
}

describe('client build time across About, System information and version exports', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    vi.stubGlobal('__APP_PKG_VERSION__', '1.0.0');
    vi.stubEnv('VITE_APP_VERSION', '1.522');
    vi.stubEnv('VITE_APP_BUILD', IMAGE_BUILD_TIME);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('uses the frontend timestamp and the same display format when image preparation started earlier', () => {
    vi.stubGlobal('__APP_VERSION__', CLIENT_BUILD_TIME);
    const views = mountBuildViews();
    expect(views.aboutVersion).toBe('1.522');
    expect(views.systemVersion).toBe(views.aboutVersion);
    expect(views.aboutTime).toBe(`formatted ${CLIENT_BUILD_TIME}`);
    expect(views.systemTime).toBe(views.aboutTime);
    expect(views.serverTime).toBe(`formatted ${SERVER_BUILD_TIME}`);
    expect(views.clientReport).toContain(`Built: ${CLIENT_BUILD_TIME}`);
    expect(views.clientReport).not.toContain(IMAGE_BUILD_TIME);
    views.about.unmount();
    views.system.unmount();
  });

  it('labels development builds and keeps their frontend timestamp', () => {
    vi.stubEnv('VITE_APP_VERSION', '');
    vi.stubGlobal('__APP_VERSION__', CLIENT_BUILD_TIME);
    const views = mountBuildViews();
    expect(views.aboutVersion).toBe('Development');
    expect(views.systemVersion).toBe(views.aboutVersion);
    expect(views.systemTime).toBe(`formatted ${CLIENT_BUILD_TIME}`);
    expect(views.clientReport).not.toContain(IMAGE_BUILD_TIME);
    views.about.unmount();
    views.system.unmount();
  });

  it('keeps a missing client timestamp unavailable without substituting the image timestamp', () => {
    vi.stubGlobal('__APP_VERSION__', undefined);
    const views = mountBuildViews();
    expect(views.aboutTime).toBe('Unavailable');
    expect(views.systemTime).toBe(views.aboutTime);
    expect(views.clientReport).toContain('Built: Unavailable');
    expect(views.clientReport).not.toContain(IMAGE_BUILD_TIME);
    views.about.unmount();
    views.system.unmount();
  });
});
