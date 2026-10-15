import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminSessionSection from '@/components/admin/AdminSessionSection.vue';

const auth = vi.hoisted(() => ({
  getAuthenticatedUsername: vi.fn(() => 'demo'),
  getTokenExpiresAt: vi.fn(() => null),
  getTokenIssuedAt: vi.fn(() => null),
  getClientLoginRef: vi.fn<() => string | null>(() => 'login-ref-1'),
  logoutAndForgetEverything: vi.fn(),
  logoutCredentialsOnly: vi.fn(),
}));
const writeTextToClipboard = vi.hoisted(() => vi.fn());

vi.mock('@/utils/auth', () => auth);
vi.mock('@/utils/clipboard', () => ({ selectInputText: vi.fn(), writeTextToClipboard }));

function mountSection() {
  return mount(AdminSessionSection, {
    global: {
      stubs: {
        Button: {
          props: ['label', 'disabled'],
          template: '<button :disabled="disabled">{{ label }}</button>',
        },
        Dialog: true,
      },
    },
  });
}

describe('current client login reference', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    auth.getClientLoginRef.mockReturnValue('login-ref-1');
    writeTextToClipboard.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => vi.useRealTimers());

  it('shows the readable reference and copies it with confirmation', async () => {
    const wrapper = mountSection();
    const input = wrapper.get<HTMLInputElement>('#admin-client-login-ref');
    expect(input.attributes('type')).toBe('text');
    expect(input.element.value).toBe('login-ref-1');
    expect(input.attributes()).toHaveProperty('readonly');
    expect(wrapper.find('input[type="password"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('It cannot grant access on its own.');

    const copy = wrapper.get('button[aria-label="Copy ClientLoginRef"]');
    await copy.trigger('click');
    await flushPromises();
    expect(writeTextToClipboard).toHaveBeenCalledExactlyOnceWith('login-ref-1');
    expect(copy.text()).toBe('Copied');
    await vi.advanceTimersByTimeAsync(1800);
    expect(copy.text()).toBe('Copy');
    wrapper.unmount();
  });

  it('disables copying when there is no login reference', () => {
    auth.getClientLoginRef.mockReturnValue(null);
    const wrapper = mountSection();
    expect(wrapper.get<HTMLInputElement>('#admin-client-login-ref').element.value).toBe('Unavailable');
    expect(wrapper.get('button[aria-label="Copy ClientLoginRef"]').attributes()).toHaveProperty('disabled');
    wrapper.unmount();
  });

  it('shows a clipboard failure without claiming the reference was copied', async () => {
    writeTextToClipboard.mockRejectedValue(new Error('Clipboard unavailable'));
    const wrapper = mountSection();
    const copy = wrapper.get('button[aria-label="Copy ClientLoginRef"]');
    await copy.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('Clipboard unavailable');
    expect(copy.text()).toBe('Copy');
    wrapper.unmount();
  });
});
