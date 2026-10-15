import { enableAutoUnmount, flushPromises as flushVuePromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia } from 'pinia';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

enableAutoUnmount(afterEach);

async function flushPromises() {
  // Status mutations and initial loads now share the store's scheduled tick.
  await flushVuePromises();
  await vi.advanceTimersByTimeAsync(STATUS_POLL_INTERVAL_MS);
  await flushVuePromises();
}

const healthApiMock = vi.hoisted(() => ({
  status: vi.fn(),
  setup: vi.fn(),
  connect: vi.fn(),
  completeConnection: vi.fn(),
  syncNow: vi.fn(),
  reconcileAll: vi.fn(),
  reloadRange: vi.fn(),
  retryFailed: vi.fn(),
  disconnect: vi.fn(),
  deleteMirror: vi.fn(),
}));

const routeMock = vi.hoisted(() => ({ path: '/admin/imports', query: {} as Record<string, string> }));
const routerMock = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router');
  return {
    ...actual,
    useRoute: () => routeMock,
    useRouter: () => routerMock,
  };
});

vi.mock('@/features/health/api/googleHealthApi', () => ({
  googleHealthApi: healthApiMock,
}));

vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      async getApplicationStatus() {
        const googleHealth = await healthApiMock.status();
        return {
          generatedAt: new Date(),
          included: new Set(['google-health']),
          googleHealth,
        };
      }
    },
  };
});

import GoogleHealthAdminCard from '@/features/health/screens/admin/GoogleHealthAdminCard.vue';

const ButtonStub = {
  props: ['label', 'disabled'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};

const InputTextStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: `<input :value="modelValue" :disabled="disabled" @input="$emit('update:modelValue', $event.target.value)" />`,
};

const RouterLinkStub = {
  props: ['custom', 'to'],
  methods: { navigate() {} },
  template: '<slot :navigate="navigate" />',
};

const SetupDialogStub = {
  props: ['visible', 'oauthResult', 'oauthMessage'],
  emits: ['update:visible', 'connect', 'complete'],
  template: '<div v-if="visible" data-testid="setup-dialog" :data-oauth-result="oauthResult" />',
};

const ReauthorizationDialogStub = {
  props: ['visible', 'result'],
  emits: ['update:visible', 'connect', 'complete'],
  template: '<div v-if="visible" data-testid="reauthorization-dialog" :data-oauth-result="result" />',
};

function mountCard(detail = true) {
  return mount(GoogleHealthAdminCard, {
    props: { active: true, demoMode: false, detail },
    global: {
      plugins: [createPinia()],
      stubs: {
        Button: ButtonStub,
        InputText: InputTextStub,
        RouterLink: RouterLinkStub,
        GoogleHealthSetupDialog: SetupDialogStub,
        GoogleHealthReauthorizationDialog: ReauthorizationDialogStub,
      },
    },
  });
}

function button(wrapper: ReturnType<typeof mountCard>, label: string) {
  const result = wrapper.findAll('button').find((candidate) => candidate.text() === label);
  if (!result) throw new Error(`Button not found: ${label}`);
  return result;
}

describe('GoogleHealthAdminCard', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    Object.values(healthApiMock).forEach((mockFunction) => mockFunction.mockReset());
    window.sessionStorage.clear();
    healthApiMock.setup.mockResolvedValue({
      configurationState: 'MISSING_CONFIGURATION',
      configurationIssues: ['CLIENT_ID_MISSING'],
      oauthMode: 'MANUAL',
      manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
      callbackPath: '/api/google-health/oauth/callback',
      requestedScopes: [],
    });
    routeMock.path = '/admin/imports';
    routeMock.query = {};
    routerMock.replace.mockReset();
    routerMock.push.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it.each(['RECONNECT_REQUIRED', 'CONNECTED'] as const)(
    'opens a renewal link only if the loaded connection needs it: %s',
    async (connectionState) => {
      routeMock.query = { connection: 'google-health', googleHealthReauthorize: 'open' };
      healthApiMock.status.mockResolvedValue({
        configurationState: 'CONFIGURED',
        connectionState,
        authorizationAction: connectionState === 'CONNECTED' ? 'NONE' : 'REAUTHORIZE',
        job: { state: 'IDLE' },
      });
      const wrapper = mountCard();
      await flushPromises();
      expect(wrapper.getComponent(ReauthorizationDialogStub).props('visible')).toBe(
        connectionState === 'RECONNECT_REQUIRED'
      );
      expect(wrapper.getComponent(SetupDialogStub).props('visible')).toBe(false);
      expect(healthApiMock.connect).not.toHaveBeenCalled();
      expect(routerMock.replace).toHaveBeenCalledWith({
        path: '/admin/imports',
        query: { connection: 'google-health' },
      });
      if (connectionState === 'CONNECTED') expect(wrapper.text()).not.toContain('In Testing mode');
    }
  );

  it('reloads selected whole days and releases failed ranges while a cycle is active', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'RUNNING' },
    });
    healthApiMock.reloadRange.mockResolvedValue({});
    healthApiMock.retryFailed.mockResolvedValue({});
    const wrapper = mountCard();
    await flushPromises();
    const dates = wrapper.findAll('input[type="date"]');
    await dates[0]!.setValue('2026-09-01');
    await dates[1]!.setValue('2026-09-05');
    expect(button(wrapper, 'Reload').attributes()).not.toHaveProperty('disabled');
    await button(wrapper, 'Reload').trigger('click');
    await flushPromises();
    expect(healthApiMock.reloadRange).toHaveBeenCalledWith('2026-09-01', '2026-09-05');
    await button(wrapper, 'Retry').trigger('click');
    await flushPromises();
    expect(healthApiMock.retryFailed).toHaveBeenCalledOnce();
  });

  it('keeps the service row compact and only loads setup when details are opened', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [{ backfillComplete: false, lastSuccessAt: new Date('2026-09-04T10:00:00Z') }],
      job: { state: 'COMPLETED' },
    });
    const wrapper = mountCard(false);
    await flushPromises();
    expect(wrapper.text()).toContain('Connected · Last synced');
    expect(wrapper.text()).toContain('History import pending');
    expect(button(wrapper, 'Manage').exists()).toBe(true);
    expect(wrapper.find('dl').exists()).toBe(false);
    expect(wrapper.find('details').exists()).toBe(false);
    expect(wrapper.find('progress').exists()).toBe(false);
    expect(healthApiMock.setup).not.toHaveBeenCalled();
    await wrapper.setProps({ detail: true });
    await flushPromises();
    expect(wrapper.text()).toContain('Authorization expires');
    expect(healthApiMock.setup).toHaveBeenCalledOnce();
  });

  it('opens reauthorization directly from the compact row', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'RECONNECT_REQUIRED',
      authorizationAction: 'REAUTHORIZE',
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard(false);
    await flushPromises();
    await button(wrapper, 'Reauthorize').trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(ReauthorizationDialogStub).props('visible')).toBe(true);
    expect(healthApiMock.setup).toHaveBeenCalledOnce();
    expect(healthApiMock.connect).not.toHaveBeenCalled();
  });

  it('shows required server setup and disables connection actions when configuration is missing', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'MISSING_CONFIGURATION',
      connectionState: 'DISCONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Server setup is required.');
    expect(button(wrapper, 'Continue setup').attributes()).not.toHaveProperty('disabled');
    expect(button(wrapper, 'Sync now').attributes()).toHaveProperty('disabled');
    wrapper.unmount();
  });

  it('shows the generic disabled-installation state outside demo mode', async () => {
    healthApiMock.setup.mockResolvedValue({
      configurationState: 'DISABLED',
      configurationIssues: [],
      oauthMode: 'MANUAL',
      manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
      callbackPath: '/api/google-health/oauth/callback',
      requestedScopes: [],
    });
    healthApiMock.status.mockResolvedValue({
      configurationState: 'DISABLED',
      connectionState: 'DISCONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Disabled');
    expect(wrapper.text()).toContain('disabled for this installation');
    expect(wrapper.text()).not.toContain('Setup required');
    expect(button(wrapper, 'Set up connection').attributes()).not.toHaveProperty('disabled');
    expect(button(wrapper, 'Sync now').attributes()).toHaveProperty('disabled');
    wrapper.unmount();
  });

  it('keeps retained mirror data accessible while the connector is disabled', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'DISABLED',
      connectionState: 'DISCONNECTED',
      grantedCategories: {},
      mirrorSummary: { recordCount: 42 },
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(button(wrapper, 'Open Health').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows a non-interactive preview in demo mode', async () => {
    const wrapper = mount(GoogleHealthAdminCard, {
      props: { active: true, demoMode: true, detail: true },
      global: {
        plugins: [createPinia()],
        stubs: {
          Button: ButtonStub,
          InputText: InputTextStub,
          RouterLink: RouterLinkStub,
          GoogleHealthSetupDialog: SetupDialogStub,
          GoogleHealthReauthorizationDialog: ReauthorizationDialogStub,
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Demo preview');
    expect(wrapper.text()).toContain('Preview only in demo mode');
    expect(wrapper.text()).toContain('cannot connect to, access, or store data from a Google account');
    expect(wrapper.text()).toContain('Activity and fitness');
    expect(button(wrapper, 'Unavailable in demo').attributes()).toHaveProperty('disabled');
    expect(wrapper.text()).not.toContain('Set up connection');
    expect(wrapper.text()).not.toContain('Delete local mirror');
    expect(wrapper.find('[data-testid="setup-dialog"]').exists()).toBe(false);
    expect(healthApiMock.status).toHaveBeenCalledTimes(1);
    expect(healthApiMock.setup).not.toHaveBeenCalled();
    expect(healthApiMock.connect).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('uses the shared status request without starting its own polling timer', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(healthApiMock.status).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(60_000);
    await flushPromises();
    expect(healthApiMock.status).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('prevents mirror deletion while a sync job is active', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: { sleep: true },
      syncStates: [],
      job: { state: 'RUNNING', totalUnits: 2, completedUnits: 1 },
    });
    const wrapper = mountCard();
    await flushPromises();
    await wrapper.get('input[placeholder="DELETE"]').setValue('DELETE');

    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.text()).toContain('Sync in progress');
    expect(button(wrapper, 'Delete local mirror').attributes()).toHaveProperty('disabled');
    wrapper.unmount();
  });

  it('links running reconciliation to Processing without duplicating detailed progress', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'RUNNING', kind: 'FULL_RECONCILIATION', totalUnits: 0, completedUnits: 0 },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Rechecking history');
    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.text()).toContain('Recent sync continues while history reloads. Published routes are kept.');
    wrapper.unmount();
  });

  it('links sync errors to Processing', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'COMPLETED_WITH_ERRORS', error: 'sleep sync failed' },
    });

    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Sync needs attention');
    expect(wrapper.text()).not.toContain('sleep sync failed');
    wrapper.unmount();
  });

  it('shows pending history outside collapsed settings after a successful pass', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [
        {
          dataType: 'heart-rate',
          representation: 'HEART_RATE_SAMPLE',
          oldestSyncedTime: new Date('2026-06-25T00:00:00Z'),
          backfillComplete: false,
        },
      ],
      job: { state: 'COMPLETED', totalUnits: 140, completedUnits: 140 },
    });
    const wrapper = mountCard();
    await flushPromises();

    const notice = wrapper.get('.gh-sync-notice');
    expect(notice.element.closest('details')).toBeNull();
    expect(notice.text()).toContain('History import pending');
    expect(wrapper.find('[data-testid="google-health-sync-progress"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('100%');
    wrapper.unmount();
  });

  it('summarizes failed history ranges instead of exposing internal sync modes', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [
        { dataType: 'exercise', representation: 'RAW', lastError: 'Google Health is temporarily unavailable' },
        {
          dataType: 'calories-in-heart-rate-zone',
          representation: 'DAILY_ROLLUP',
          lastError: 'Google Health is temporarily unavailable',
        },
      ],
      job: {
        state: 'COMPLETED_WITH_ERRORS',
        error:
          'exercise (RAW): Google Health is temporarily unavailable; calories-in-heart-rate-zone (DAILY_ROLLUP): Google Health is temporarily unavailable',
      },
    });

    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('History ranges awaiting retry');
    expect(wrapper.text()).not.toContain('(RAW)');
    expect(wrapper.text()).not.toContain('(DAILY_ROLLUP)');
    wrapper.unmount();
  });

  it('reopens setup after OAuth and removes the consumed result parameters', async () => {
    routeMock.query = { googleHealthSetup: 'open', googleHealth: 'denied', section: 'imports' };
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'DISCONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.get('[data-testid="setup-dialog"]').attributes('data-oauth-result')).toBe('denied');
    expect(routerMock.replace).toHaveBeenCalledWith({ path: '/admin/imports', query: { section: 'imports' } });
    wrapper.unmount();
  });

  it('requires the exact destructive confirmation before deleting the mirror', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'DISCONNECTED',
      mirrorSummary: { recordCount: 12345 },
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.deleteMirror.mockResolvedValue(undefined);
    const wrapper = mountCard();
    await flushPromises();
    const input = wrapper.get('input[placeholder="DELETE"]');
    expect(wrapper.text()).toContain('12,345 mirrored records');
    expect(wrapper.text()).toContain('all Google Health-managed route files and tracks');
    expect(wrapper.text()).toContain('Other imported tracks and the original data in Google Health are kept');

    await input.setValue('delete');
    expect(button(wrapper, 'Delete local mirror').attributes()).toHaveProperty('disabled');
    await input.setValue('DELETE');
    expect(button(wrapper, 'Delete local mirror').attributes()).not.toHaveProperty('disabled');
    await button(wrapper, 'Delete local mirror').trigger('click');
    await flushPromises();

    expect(healthApiMock.deleteMirror).toHaveBeenCalledWith('DELETE');
    wrapper.unmount();
  });

  it('keeps the retry link outside advanced connection settings', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [{ dataType: 'sleep', lastError: 'Temporary failure' }],
      job: { state: 'COMPLETED_WITH_ERRORS' },
    });
    const wrapper = mountCard();
    await flushPromises();
    const notice = wrapper.get('.gh-sync-notice');
    expect(notice.element.closest('details')).toBeNull();
    expect(notice.text()).toContain('History ranges awaiting retry');
    expect(wrapper.find('[aria-label="Google Health ranges awaiting another attempt"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('requires disconnection before accepting mirror deletion', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Disconnect Google Health first');
    expect(wrapper.get('input[placeholder="DELETE"]').attributes()).toHaveProperty('disabled');
    expect(button(wrapper, 'Delete local mirror').attributes()).toHaveProperty('disabled');
    expect(healthApiMock.deleteMirror).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('opens manual consent in a new tab and submits the pasted return address', async () => {
    const replace = vi.fn();
    const close = vi.fn();
    const oauthWindow = { opener: window, location: { replace }, close };
    const open = vi.spyOn(window, 'open').mockReturnValue(oauthWindow as unknown as Window);
    healthApiMock.setup.mockResolvedValue({
      configurationState: 'CONFIGURED',
      configurationIssues: [],
      oauthMode: 'MANUAL',
      manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
      callbackPath: '/api/google-health/oauth/callback',
      requestedScopes: [],
    });
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'DISCONNECTED',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.connect.mockResolvedValue({
      authorizationUrl: 'https://accounts.google.test/authorize',
      oauthMode: 'MANUAL',
      intent: 'CONNECT',
    });
    healthApiMock.completeConnection.mockResolvedValue({ result: 'CONNECTED' });
    const wrapper = mountCard();
    await flushPromises();
    const dialog = wrapper.getComponent(SetupDialogStub);

    dialog.vm.$emit('connect');
    await flushPromises();
    expect(healthApiMock.connect).toHaveBeenCalledWith(false);
    expect(open).toHaveBeenCalledWith('about:blank', 'mtl-google-health-oauth');
    expect(oauthWindow.opener).toBeNull();
    expect(replace).toHaveBeenCalledWith('https://accounts.google.test/authorize');

    const redirectUrl = 'http://127.0.0.1:1/mtl-explorer-oauth?code=code&state=state';
    dialog.vm.$emit('complete', redirectUrl);
    await flushPromises();
    expect(healthApiMock.completeConnection).toHaveBeenCalledWith(redirectUrl);
    expect(dialog.props('oauthResult')).toBe('connected');
    wrapper.unmount();
    open.mockRestore();
  });

  it('uses the short reauthorization flow and keeps the existing mirror', async () => {
    const expiry = new Date('2026-09-08T12:00:00Z');
    const replace = vi.fn();
    const oauthWindow = { opener: window, location: { replace }, close: vi.fn() };
    const open = vi.spyOn(window, 'open').mockReturnValue(oauthWindow as unknown as Window);
    healthApiMock.setup.mockResolvedValue({
      configurationState: 'CONFIGURED',
      configurationIssues: [],
      oauthMode: 'MANUAL',
      manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
      callbackPath: '/api/google-health/oauth/callback',
      requestedScopes: [],
    });
    healthApiMock.status
      .mockResolvedValueOnce({
        configurationState: 'CONFIGURED',
        connectionState: 'RECONNECT_REQUIRED',
        authorizationAction: 'REAUTHORIZE',
        refreshTokenExpiresAt: expiry,
        grantedCategories: {},
        syncStates: [{ dataType: 'sleep' }],
        job: { state: 'IDLE' },
      })
      .mockResolvedValue({
        configurationState: 'CONFIGURED',
        connectionState: 'CONNECTED',
        authorizationAction: 'NONE',
        grantedCategories: {},
        syncStates: [{ dataType: 'sleep' }],
        job: { state: 'IDLE' },
      });
    healthApiMock.connect.mockResolvedValue({
      authorizationUrl: 'https://accounts.google.test/authorize',
      oauthMode: 'MANUAL',
      intent: 'REAUTHORIZE',
    });
    healthApiMock.completeConnection.mockResolvedValue({ result: 'CONNECTED' });
    const wrapper = mountCard();
    await flushPromises();

    expect(wrapper.text()).toContain('Google Health authorization has expired.');
    expect(wrapper.text()).toContain('Authorization expires');
    expect(button(wrapper, 'Reauthorize').exists()).toBe(true);
    await button(wrapper, 'Reauthorize').trigger('click');
    const dialog = wrapper.getComponent(ReauthorizationDialogStub);
    expect(dialog.props('visible')).toBe(true);

    dialog.vm.$emit('connect');
    await flushPromises();
    expect(healthApiMock.connect).toHaveBeenCalledWith(true);
    expect(replace).toHaveBeenCalledWith('https://accounts.google.test/authorize');

    const redirectUrl = 'http://127.0.0.1:1/mtl-explorer-oauth?code=code&state=state';
    dialog.vm.$emit('complete', redirectUrl);
    await flushPromises();
    expect(healthApiMock.completeConnection).toHaveBeenCalledWith(redirectUrl);
    expect(dialog.props('result')).toBe('connected');
    expect(wrapper.text()).toContain('Open Health');
    expect(healthApiMock.deleteMirror).not.toHaveBeenCalled();

    wrapper.unmount();
    open.mockRestore();
  });

  it.each([false, true])('shows route recovery details in the connection card (detail=%s)', async (detail) => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { state: 'COMPLETED' },
      routeSummary: {
        failedCount: 2,
        lastError: 'Route export is unavailable.',
        nextRetryAt: new Date('2026-09-05T12:30:00Z'),
      },
    });
    const wrapper = mountCard(detail);
    await flushPromises();
    expect(wrapper.text()).toContain('2 route imports need retry');
    expect(wrapper.text()).toContain('Route export is unavailable.');
    expect(wrapper.text()).toContain('Retry during a sync after');
  });

  it.each(['DISABLED', 'MISSING_CONFIGURATION'])(
    'blocks remote sync but permits local cleanup when %s retains credentials',
    async (configurationState) => {
      const savedConnection = {
        configurationState,
        connectionState: 'CONNECTED',
        mirrorSummary: { recordCount: 42 },
        grantedCategories: {},
        syncStates: [],
        job: { state: 'IDLE' },
      };
      healthApiMock.status.mockResolvedValue(savedConnection);
      healthApiMock.disconnect.mockResolvedValue(undefined);
      healthApiMock.deleteMirror.mockResolvedValue(undefined);
      const wrapper = mountCard();
      await flushPromises();
      expect(button(wrapper, 'Sync now').attributes()).toHaveProperty('disabled');
      expect(button(wrapper, 'Start').attributes()).toHaveProperty('disabled');
      expect(button(wrapper, 'Reconnect').attributes()).toHaveProperty('disabled');
      expect(button(wrapper, 'Disconnect').attributes()).not.toHaveProperty('disabled');
      expect(button(wrapper, 'Open Health').exists()).toBe(true);
      await button(wrapper, 'Sync now').trigger('click');
      await button(wrapper, 'Start').trigger('click');
      expect(healthApiMock.syncNow).not.toHaveBeenCalled();
      expect(healthApiMock.reconcileAll).not.toHaveBeenCalled();
      healthApiMock.status.mockResolvedValue({ ...savedConnection, connectionState: 'DISCONNECTED' });
      await button(wrapper, 'Disconnect').trigger('click');
      await flushPromises();
      expect(healthApiMock.disconnect).toHaveBeenCalledOnce();
      await wrapper.get('input[placeholder="DELETE"]').setValue('DELETE');
      expect(button(wrapper, 'Delete local mirror').attributes()).not.toHaveProperty('disabled');
      await button(wrapper, 'Delete local mirror').trigger('click');
      await flushPromises();
      expect(healthApiMock.deleteMirror).toHaveBeenCalledWith('DELETE');
      expect(healthApiMock.connect).not.toHaveBeenCalled();
    }
  );

  it('opens setup guidance instead of reauthorization when disabled credentials need reconnecting', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'DISABLED',
      connectionState: 'RECONNECT_REQUIRED',
      authorizationAction: 'REAUTHORIZE',
      job: { state: 'IDLE' },
      syncStates: [],
    });
    const wrapper = mountCard(false);
    await flushPromises();
    expect(wrapper.text()).toContain('Disabled for this installation');
    expect(wrapper.findAll('button').some((candidate) => candidate.text() === 'Reauthorize')).toBe(false);
    await button(wrapper, 'Start setup').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="setup-dialog"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="reauthorization-dialog"]').exists()).toBe(false);
    const setup = wrapper.findComponent(SetupDialogStub);
    setup.vm.$emit('connect');
    setup.vm.$emit('complete', 'http://127.0.0.1:1/mtl-explorer-oauth?code=synthetic');
    await flushPromises();
    expect(healthApiMock.connect).not.toHaveBeenCalled();
    expect(healthApiMock.completeConnection).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('Enable and configure Google Health on the server before authorizing access.');
  });
});
