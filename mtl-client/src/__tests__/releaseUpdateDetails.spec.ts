import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReleaseUpdateResponse } from 'x8ing-mtl-api-typescript-fetch';
import ReleaseUpdateDetails from '@/components/info/ReleaseUpdateDetails.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';

vi.mock('@/utils/auth', () => ({ isAuthenticated: () => false, getAuthenticatedUsername: () => 'test-user' }));
vi.mock('@/utils/Utils', () => ({ formatDateAndTime: () => 'Today' }));

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
});

describe('component update details', () => {
  it('explains a search-only offer and provider ownership in one release view', () => {
    const updates = useReleaseUpdatesStore();
    updates.applyStatus({
      status: 'UPDATE_AVAILABLE',
      installationId: 'test-installation',
      channel: 'stable',
      installedReleaseVersion: '0.9.0',
      latestReleaseVersion: '0.9.1',
      installedBuildId: '1.508',
      latestBuildId: '1.508',
      components: [
        {
          id: 'app',
          label: 'MTL Explorer',
          management: 'SELF_MANAGED',
          status: 'CURRENT',
          installedBuildId: '1.508',
          targetBuildId: '1.508',
          compatibility: 'SUPPORTED',
        },
        {
          id: 'location-search',
          label: 'Location search',
          management: 'SELF_MANAGED',
          status: 'UPDATE_AVAILABLE',
          installedBuildId: '2.1',
          targetBuildId: '2.2',
          compatibility: 'SUPPORTED',
        },
        {
          id: 'map-server',
          label: 'Maps',
          management: 'PROVIDER_MANAGED',
          status: 'PROVIDER_MANAGED',
          compatibility: 'UNKNOWN',
        },
      ],
    } as ReleaseUpdateResponse);
    const view = mount(ReleaseUpdateDetails);
    const rows = view.findAll('.release-details__components li');
    expect(rows[0].text()).toContain('Current');
    expect(rows[1].text()).toContain('Installed: 2.1');
    expect(rows[1].text()).toContain('Release target: 2.2');
    expect(rows[1].text()).toContain('remains supported');
    expect(rows[2].text()).toContain('The service provider manages updates.');
    expect(rows[2].text()).not.toContain('Update available');
    expect(view.findAll('button').filter((button) => button.text() === 'Skip this version')).toHaveLength(1);
  });

  it('explains cached observations without claiming failure or incompatibility', () => {
    const updates = useReleaseUpdatesStore();
    updates.applyStatus({
      status: 'UNKNOWN',
      components: [
        {
          id: 'location-search',
          label: 'Location search',
          management: 'SELF_MANAGED',
          status: 'UNKNOWN',
          checkFailed: true,
          compatibility: 'UNKNOWN',
        },
      ],
    } as ReleaseUpdateResponse);
    const view = mount(ReleaseUpdateDetails);
    expect(view.text()).toContain('Update status unknown');
    expect(view.text()).toContain('Any displayed build is cached.');
    expect(view.text()).not.toContain('Update this component');
    expect(view.text()).not.toContain('up to date');
  });
});
