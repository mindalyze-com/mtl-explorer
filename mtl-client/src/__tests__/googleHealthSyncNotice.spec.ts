import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import GoogleHealthSyncNotice from '@/features/health/components/GoogleHealthSyncNotice.vue';
import { formatHealthDateTime } from '@/features/health/utils/healthDateTime';
import type { GHStatusResponse } from '@/features/health/api/googleHealthApi';
import { GOOGLE_HEALTH_REAUTHORIZATION_ROUTE } from '@/features/health/utils/googleHealthNavigation';

enableAutoUnmount(afterEach);

function mountNotice(status: GHStatusResponse) {
  return mount(GoogleHealthSyncNotice, {
    props: { status },
    global: { stubs: { RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :href="to"><slot /></a>' } } },
  });
}

const retryAt = new Date('2026-09-05T12:30:00Z');
const routeFailure = { failedCount: 2, lastError: 'Route export is unavailable.', nextRetryAt: retryAt };

describe('GoogleHealthSyncNotice', () => {
  it('explains renewal with a direct recovery link and removes it once access is restored', async () => {
    const wrapper = mountNotice({
      configurationState: 'CONFIGURED',
      connectionState: 'RECONNECT_REQUIRED',
      authorizationAction: 'REAUTHORIZE',
      refreshTokenExpiresAt: new Date('2000-01-01T00:00:00Z'),
      job: { state: 'FAILED' },
    });
    expect(wrapper.text()).toContain('Google Health authorization has expired.');
    expect(wrapper.text()).toContain('Google limits access to seven days');
    expect(wrapper.text()).toContain('Stored records and routes are kept');
    expect(wrapper.text()).not.toContain('Sync needs attention');
    expect(wrapper.findComponent({ name: 'RouterLink' }).props('to')).toEqual(GOOGLE_HEALTH_REAUTHORIZATION_ROUTE);
    await wrapper.setProps({
      status: { configurationState: 'CONFIGURED', connectionState: 'CONNECTED', authorizationAction: 'NONE' },
    });
    expect(wrapper.find('.gh-authorization-notice').exists()).toBe(false);
  });

  it('does not show renewal explanations for synthetic demo data', async () => {
    const wrapper = mountNotice({ configurationState: 'CONFIGURED', connectionState: 'RECONNECT_REQUIRED' });
    await wrapper.setProps({ syntheticData: true });
    expect(wrapper.text()).toContain('View demo generation progress');
    expect(wrapper.find('.gh-authorization-notice').exists()).toBe(false);
  });

  it('shows outstanding route failures even after a completed data sync', () => {
    const wrapper = mountNotice({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED' },
      routeSummary: routeFailure,
    });
    expect(wrapper.get('[role="status"]').text()).toContain('2 route imports need retry');
    expect(wrapper.text()).toContain('Route export is unavailable.');
    expect(wrapper.text()).toContain(`Retry during a sync after ${formatHealthDateTime(retryAt)}.`);
    expect(wrapper.findComponent({ name: 'RouterLink' }).props('to')).toEqual(
      expect.objectContaining({
        params: { section: 'processing' },
      })
    );
  });

  it('keeps route failures visible while another sync runs', () => {
    const wrapper = mountNotice({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'RUNNING' },
      routeSummary: { failedCount: 1 },
    });
    expect(wrapper.text()).toContain('Sync in progress');
    expect(wrapper.text()).toContain('1 route import needs retry');
  });

  it.each([
    { configurationState: 'DISABLED', connectionState: 'CONNECTED' },
    { configurationState: 'CONFIGURED', connectionState: 'DISCONNECTED' },
  ] as const)('explains paused retries for $configurationState / $connectionState', (connection) => {
    const wrapper = mountNotice({ ...connection, routeSummary: routeFailure });
    expect(wrapper.text()).toContain('Route retries are paused until Google Health is enabled and connected.');
    expect(wrapper.text()).not.toContain('Retry during a sync after');
  });

  it('does not report ordinary pending or no-route exercises as failures', () => {
    const wrapper = mountNotice({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED' },
      routeSummary: { failedCount: 0, pendingCount: 3 },
    });
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
  });

  it('directs missing route permissions to connection settings', () => {
    const wrapper = mountNotice({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      grantedCategories: { activity: true, location: false },
      routeSummary: routeFailure,
    });
    expect(wrapper.text()).toContain('Route retries need Activity and Location access.');
    expect(wrapper.findComponent({ name: 'RouterLink' }).props('to')).toEqual(
      expect.objectContaining({
        params: { section: 'imports' },
        query: { connection: 'google-health' },
      })
    );
    expect(wrapper.text()).not.toContain('Retry during a sync after');
  });
});
