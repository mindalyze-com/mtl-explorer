import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GoogleHealthSetupDialog from '@/features/health/screens/admin/GoogleHealthSetupDialog.vue';

const clipboardMock = vi.hoisted(() => vi.fn());
vi.mock('@/utils/clipboard', () => ({ writeTextToClipboard: clipboardMock }));

const BottomSheetStub = {
  name: 'BottomSheet',
  props: ['modelValue', 'title', 'sheetClass'],
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
};
const ButtonStub = {
  props: ['label', 'disabled'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};

const scopes = [
  'https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly',
  'https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly',
  'https://www.googleapis.com/auth/googlehealth.sleep.readonly',
  'https://www.googleapis.com/auth/googlehealth.location.readonly',
];
const configuredSetup = {
  configurationState: 'CONFIGURED' as const,
  configurationIssues: [],
  oauthMode: 'MANUAL' as const,
  manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
  callbackPath: '/api/google-health/oauth/callback',
  requestedScopes: scopes,
};

function mountDialog(
  status: Record<string, unknown> = { connectionState: 'DISCONNECTED', grantedCategories: {} },
  setup = configuredSetup
) {
  return mount(GoogleHealthSetupDialog, {
    props: { visible: true, setup, status },
    global: { stubs: { BottomSheet: BottomSheetStub, Button: ButtonStub, teleport: true } },
  });
}

function button(wrapper: ReturnType<typeof mountDialog>, label: string) {
  const result = wrapper.findAll('button').find((candidate) => candidate.text().includes(label));
  if (!result) throw new Error(`Button not found: ${label}`);
  return result;
}

describe('GoogleHealthSetupDialog', () => {
  beforeEach(() => {
    window.localStorage.clear();
    clipboardMock.mockReset();
    clipboardMock.mockResolvedValue(undefined);
  });

  it('explains the privacy tradeoff, Testing expiry, and the short reauthorization path before setup', async () => {
    const wrapper = mountDialog();

    expect(wrapper.text()).toContain('Before you start');
    expect(wrapper.text()).not.toContain('Step 0');
    expect(wrapper.text()).toContain('Private by design');
    expect(wrapper.text()).toContain('No shared MTL Explorer service handles or inspects it');
    expect(wrapper.text()).toContain('A direct connection requires installation-specific Google Cloud and OAuth setup');
    expect(wrapper.text()).toContain('Testing-mode authorization expires after seven days');
    expect(wrapper.text()).toContain('existing mirrored data is kept');
    expect(wrapper.text()).toContain('Reauthorization repeats only the final consent step');

    await button(wrapper, 'Start setup').trigger('click');
    expect(wrapper.text()).toContain('Step 1 of 7');
    expect(wrapper.text()).toContain('Project and API');
  });

  it('matches the Google Auth Platform navigation order', () => {
    const wrapper = mountDialog();

    expect(wrapper.findAll('.gh-setup-step__label').map((step) => step.text())).toEqual([
      'Project and API',
      'Branding',
      'Audience and test user',
      'OAuth client',
      'Data access',
      'Server configuration',
      'Connect and verify',
    ]);
  });

  it('requires confirmation that the Google Health account was added as a test user', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'Audience and test user').trigger('click');

    expect(wrapper.text()).toContain('Google authorizes only accounts listed under Test users');
    expect(wrapper.text()).toContain('access_denied');
    expect(wrapper.text()).toContain("MTL Explorer cannot check your project's test-user list");
    expect(wrapper.text()).toContain('Add the account that owns the health data');
    expect(wrapper.text()).toContain('+ Add users');
    expect(wrapper.text()).toContain('confirm the account appears in the Test users list');
    expect(wrapper.text()).toContain('I added the Google Health account under Test users');
    expect(wrapper.text()).toContain('warns that the app is unverified');
    expect(wrapper.text()).toContain('Why use Testing mode?');
    expect(wrapper.text()).toContain('need to publish the app or have it verified');
    expect(wrapper.text()).toContain('Internal audience');
    expect(wrapper.text()).not.toContain('Application home page');
    expect(wrapper.get('a[href="https://console.cloud.google.com/auth/audience"]').attributes('target')).toBe('_blank');
    expect(button(wrapper, 'Next').attributes()).toHaveProperty('disabled');

    await wrapper.get('.gh-setup-complete input').setValue(true);
    expect(button(wrapper, 'Next').attributes()).not.toHaveProperty('disabled');
  });

  it('separates required scopes from optional route location and copies all scopes', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'Data access').trigger('click');

    expect(wrapper.text()).toContain('Required');
    expect(wrapper.text()).toContain('Optional for exercise routes');
    await button(wrapper, 'Copy all four scopes').trigger('click');
    await flushPromises();
    expect(clipboardMock).toHaveBeenCalledWith(scopes.join('\n'));
  });

  it('links denied consent back to the test-user setup without assuming the cause', async () => {
    const wrapper = mountDialog();
    await wrapper.setProps({ oauthResult: 'denied', oauthMessage: 'Google authorization was cancelled or denied.' });
    expect(wrapper.text()).toContain('Google authorization was cancelled or denied.');
    expect(wrapper.text()).toContain('If you did not decline access, check Audience → Test users');
  });

  it('uses the Google CLI Desktop client flow and only two environment values', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'OAuth client').trigger('click');

    expect(wrapper.text()).toContain('Choose Desktop app');
    expect(wrapper.text()).toContain("Google's own Health CLI");
    expect(wrapper.text()).toContain('do not need an Authorized JavaScript origin or Authorized redirect URI');
    expect(wrapper.text()).toContain('Client ID');
    expect(wrapper.text()).toContain('Client secret');

    await button(wrapper, 'Server configuration').trigger('click');
    await button(wrapper, 'Copy settings').trigger('click');
    await button(wrapper, 'Copy command').trigger('click');
    await flushPromises();
    const environment = String(clipboardMock.mock.calls[0]?.[0]);
    expect(environment).toContain('MTL_GOOGLE_HEALTH_CLIENT_ID=');
    expect(environment).toContain('MTL_GOOGLE_HEALTH_CLIENT_SECRET=');
    expect(environment).not.toContain('MTL_GOOGLE_HEALTH_REDIRECT_URI=');
    expect(clipboardMock).toHaveBeenCalledWith('docker compose up -d');
    expect(wrapper.text()).toContain('not only docker compose restart');
  });

  it('opens compact WebP references in a dismissible large preview', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'Project and API').trigger('click');

    const apiReference = wrapper.get('button[aria-label="Open Google Cloud showing Google Health API enabled larger"]');
    expect(apiReference.get('img').attributes('src')).toContain('.webp');
    expect(apiReference.text()).toContain('View larger');

    await apiReference.trigger('click');
    expect(wrapper.get('[role="dialog"]').text()).toContain('The selected project should show API Enabled');
    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);

    await button(wrapper, 'Connect and verify').trigger('click');
    expect(wrapper.text()).toContain('Select what this app can access');
    expect(wrapper.text()).toContain('Select all');
    expect(wrapper.text()).toContain('GPS location is optional');
    expect(
      wrapper
        .get('img[alt="Google final approval screen with all four Google Health categories selected"]')
        .attributes('src')
    ).toContain('06-google-consent');
  });

  it('keeps the existing proxy callback as an advanced path', async () => {
    const wrapper = mountDialog(undefined, {
      ...configuredSetup,
      oauthMode: 'PROXY' as const,
      configuredRedirectUri: 'https://example.test/mtl/api/google-health/oauth/callback',
    });
    await button(wrapper, 'OAuth client').trigger('click');

    expect(wrapper.text()).toContain('advanced proxy callback');
    expect(wrapper.text()).toContain('Web application');
    expect(wrapper.text()).toContain('/api/google-health/oauth/callback');
    expect(
      wrapper.get('img[alt="Google Cloud form for creating a Web application OAuth client"]').attributes('src')
    ).toContain('.webp');
  });

  it('submits only the complete copied loopback address', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'Connect and verify').trigger('click');

    expect(wrapper.text()).toContain("can't reach this page");
    expect(wrapper.text()).toContain('Cmd/Ctrl+L');
    expect(wrapper.text()).toContain('Developer tools and the error-page body are not needed');
    expect(wrapper.get('textarea').attributes('placeholder')).toBe(
      'http://127.0.0.1:1/mtl-explorer-oauth?code=...&state=...'
    );
    const redirectUrl = 'http://127.0.0.1:1/mtl-explorer-oauth?code=code&state=state';
    await wrapper.get('textarea').setValue(redirectUrl);
    await button(wrapper, 'Complete connection').trigger('click');
    expect(wrapper.emitted('complete')).toEqual([[redirectUrl]]);
  });

  it('explains an invalid pasted address before enabling completion', async () => {
    const wrapper = mountDialog();
    await button(wrapper, 'Connect and verify').trigger('click');
    const textarea = wrapper.get('textarea');

    await textarea.setValue('http://localhost:1/?code=code&state=state');

    expect(textarea.attributes('aria-invalid')).toBe('true');
    expect(wrapper.text()).toContain('Paste the complete http://127.0.0.1:1/mtl-explorer-oauth address');
    expect(button(wrapper, 'Complete connection').attributes()).toHaveProperty('disabled');
    expect(wrapper.emitted('complete')).toBeUndefined();
  });

  it('shows the reported Testing expiry after connection', async () => {
    const expiry = new Date('2026-09-08T12:00:00Z');
    const wrapper = mount(GoogleHealthSetupDialog, {
      props: {
        visible: true,
        setup: configuredSetup,
        oauthResult: 'connected',
        status: {
          connectionState: 'CONNECTED',
          refreshTokenExpiresAt: expiry,
          grantedCategories: { sleep: true, location: false },
          job: {
            state: 'RUNNING',
            totalUnits: 80,
            completedUnits: 20,
            currentDataType: 'sleep',
            currentRepresentation: 'RAW',
            currentWindowImported: 12,
          },
          syncStates: [],
        },
      },
      global: { stubs: { BottomSheet: BottomSheetStub, Button: ButtonStub, teleport: true } },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('The first sync has started');
    expect(wrapper.text()).toContain('The first pass can take several minutes');
    expect(wrapper.text()).toContain('Older history continues over later syncs and can take hours');
    expect(wrapper.text()).toContain('Current pass: 20 of 80 steps');
    expect(wrapper.text()).toContain('12 records processed in this window');
    expect(wrapper.text()).toContain('You can close this window; syncing continues');
    expect(wrapper.text()).toContain('authorization expires on');
    expect(wrapper.text()).toContain('September');
    expect(wrapper.text()).toContain('exercise routes cannot be imported');
  });
});
