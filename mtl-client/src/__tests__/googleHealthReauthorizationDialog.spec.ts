import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import GoogleHealthReauthorizationDialog from '@/features/health/screens/admin/GoogleHealthReauthorizationDialog.vue';

const BottomSheetStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
};
const ButtonStub = {
  props: ['label', 'disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};
const setup = {
  configurationState: 'CONFIGURED' as const,
  configurationIssues: [],
  oauthMode: 'MANUAL' as const,
  manualRedirectUri: 'http://127.0.0.1:1/mtl-explorer-oauth',
  callbackPath: '/api/google-health/oauth/callback',
  requestedScopes: [],
};

function mountDialog(result = '', error = '') {
  return mount(GoogleHealthReauthorizationDialog, {
    props: { visible: true, setup, result, error },
    global: { stubs: { BottomSheet: BottomSheetStub, Button: ButtonStub } },
  });
}

describe('GoogleHealthReauthorizationDialog', () => {
  it('explains expiry only when the current connection needs renewal', async () => {
    const wrapper = mountDialog();
    expect(wrapper.text()).not.toContain('Testing mode');
    await wrapper.setProps({
      status: {
        configurationState: 'CONFIGURED',
        connectionState: 'RECONNECT_REQUIRED',
        authorizationAction: 'REAUTHORIZE',
        refreshTokenExpiresAt: new Date('2000-01-01T00:00:00Z'),
      },
    });
    expect(wrapper.text()).toContain('Google Health authorization has expired.');
    expect(wrapper.text()).toContain('Your Google Cloud and server setup can stay in place');
    expect(wrapper.text()).toContain('Copy the complete address bar and paste it below');
  });

  it('uses the exact loopback return address and emits the complete URL', async () => {
    const wrapper = mountDialog();
    const textarea = wrapper.get('textarea');
    expect(textarea.attributes('placeholder')).toBe('http://127.0.0.1:1/mtl-explorer-oauth?code=...&state=...');
    const redirectUrl = 'http://127.0.0.1:1/mtl-explorer-oauth?code=code&state=state';
    await textarea.setValue(redirectUrl);
    const completeButton = wrapper.findAll('button').find((button) => button.text() === 'Complete reauthorization');
    await completeButton?.trigger('click');
    expect(wrapper.emitted('complete')).toEqual([[redirectUrl]]);
  });

  it('rejects a pasted address without the OAuth state', async () => {
    const wrapper = mountDialog();
    const textarea = wrapper.get('textarea');
    await textarea.setValue('http://127.0.0.1:1/mtl-explorer-oauth?code=code');

    expect(textarea.attributes('aria-invalid')).toBe('true');
    expect(wrapper.text()).toContain('missing the Google authorization state');
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Complete reauthorization')
        ?.attributes()
    ).toHaveProperty('disabled');
    expect(wrapper.emitted('complete')).toBeUndefined();
  });

  it('shows a returned OAuth error and the successful mirror-preservation result', async () => {
    const failed = mountDialog('error', 'The selected Google account differs from the local mirror.');
    expect(failed.text()).toContain('The selected Google account differs from the local mirror.');

    await failed.setProps({ result: 'connected', error: '' });
    expect(failed.text()).toContain('Existing mirrored data was kept');
    expect(failed.find('textarea').exists()).toBe(false);
  });
});
