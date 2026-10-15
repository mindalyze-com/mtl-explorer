import { createPinia, setActivePinia } from 'pinia';
import { mount, type VueWrapper } from '@vue/test-utils';
import { reactive } from 'vue';
import PrimeVue from 'primevue/config';
import ToastService from 'primevue/toastservice';
import { useToast } from 'primevue/usetoast';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ReleaseUpdateNotifications from '@/components/info/ReleaseUpdateNotifications.vue';
import AppNotifications from '@/components/ui/AppNotifications.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { STORAGE_KEYS } from '@/utils/appStorage';
import type { ReleaseUpdateResponse } from 'x8ing-mtl-api-typescript-fetch';

const auth = vi.hoisted(() => ({ signedIn: false }));
const route = reactive({ name: 'login' });
const views: VueWrapper[] = [];
vi.mock('@/utils/auth', () => ({ isAuthenticated: () => auth.signedIn, getAuthenticatedUsername: () => 'test' }));
vi.mock('vue-router', () => ({ useRoute: () => route }));

beforeEach(() => {
  vi.useFakeTimers();
  localStorage.clear();
  setActivePinia(createPinia());
  auth.signedIn = false;
  route.name = 'login';
});
afterEach(() => {
  views.splice(0).forEach((view) => view.unmount());
  vi.useRealTimers();
  vi.restoreAllMocks();
});

function notice(browserUpdateAvailable: boolean, browserJustUpdated = false) {
  const view = mount(
    {
      components: { AppNotifications, ReleaseUpdateNotifications },
      props: { browserUpdateAvailable: Boolean, browserJustUpdated: Boolean },
      emits: ['reload', 'later'],
      setup: () => ({ toast: useToast() }),
      template: `<AppNotifications />
        <ReleaseUpdateNotifications :browser-update-available="browserUpdateAvailable" :browser-just-updated="browserJustUpdated"
          @reload="$emit('reload')" @later="$emit('later')" />`,
    },
    {
      props: { browserUpdateAvailable, browserJustUpdated },
      global: {
        plugins: [[PrimeVue, { unstyled: true }], ToastService],
        stubs: { BottomSheet: true, ReleaseUpdateDetails: true },
      },
    }
  );
  views.push(view);
  return view;
}

function cards() {
  return Array.from(document.querySelectorAll('.notification-card'));
}

function availableRelease(version = '0.9.0', build = '1.520'): ReleaseUpdateResponse {
  return {
    installationId: 'installation-a',
    channel: 'stable',
    status: 'UPDATE_AVAILABLE',
    installedBuildId: '1.508',
    installedReleaseVersion: '0.8.1',
    latestBuildId: build,
    latestReleaseVersion: version,
  } as ReleaseUpdateResponse;
}

function noticedRelease() {
  return JSON.parse(localStorage.getItem(STORAGE_KEYS.releaseUpdates) ?? '{}')['installation-a:test']?.noticed;
}

describe('release update notifications', () => {
  it('offers reload and later before login without starting authenticated checks', async () => {
    const updates = useReleaseUpdatesStore();
    const start = vi.spyOn(updates, 'startChecks');
    const view = notice(true);
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()[0].textContent).toContain('MTL Explorer update ready');
    (cards()[0].querySelectorAll('button')[0] as HTMLButtonElement).click();
    expect(view.emitted('reload')).toHaveLength(1);
    (cards()[0].querySelectorAll('button')[1] as HTMLButtonElement).click();
    expect(view.emitted('later')).toHaveLength(1);
    await view.setProps({ browserUpdateAvailable: false });
    expect(cards()).toHaveLength(0);
    expect(start).not.toHaveBeenCalled();
  });

  it('keeps release offers hidden on login even if a cached request finishes', async () => {
    notice(false);
    const updates = useReleaseUpdatesStore();
    updates.noticeVisible = true;
    updates.installedNoticeVisible = true;
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()).toHaveLength(0);
  });

  it('starts checks after login and stops them on logout while retaining browser reload', async () => {
    const updates = useReleaseUpdatesStore();
    const stop = vi.fn();
    const start = vi.spyOn(updates, 'startChecks').mockReturnValue(stop);
    const view = notice(true);
    await vi.advanceTimersByTimeAsync(2500);
    auth.signedIn = true;
    route.name = 'map';
    await view.vm.$nextTick();
    expect(start).toHaveBeenCalledOnce();
    auth.signedIn = false;
    route.name = 'login';
    await view.vm.$nextTick();
    expect(stop).toHaveBeenCalledOnce();
    expect(cards()[0].textContent).toContain('MTL Explorer update ready');
  });

  it('stacks a timed message with a persistent reload prompt and dismisses them independently', async () => {
    const view = notice(true);
    await vi.advanceTimersByTimeAsync(2500);
    view.vm.toast.add({ severity: 'error', summary: 'Download failed', life: 4000 });
    await view.vm.$nextTick();
    expect(cards()).toHaveLength(2);
    expect(document.querySelectorAll('.app-notifications')).toHaveLength(1);
    expect(cards()[1].parentElement?.getAttribute('role')).toBe('alert');
    expect(cards()[0].parentElement?.getAttribute('aria-live')).toBe('polite');
    (cards()[1].querySelector('button') as HTMLButtonElement).click();
    await view.vm.$nextTick();
    expect(cards()).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(cards()[0].textContent).toContain('Reload');
    view.vm.toast.add({ severity: 'success', summary: 'Saved', life: 1800 });
    await view.vm.$nextTick();
    expect(cards()).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1000);
    cards()[1].parentElement?.dispatchEvent(new MouseEvent('mouseenter'));
    await vi.advanceTimersByTimeAsync(2000);
    expect(cards()).toHaveLength(2);
    cards()[1].parentElement?.dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(800);
    expect(cards()).toHaveLength(1);
    await view.setProps({ browserUpdateAvailable: false });
    expect(cards()).toHaveLength(0);
  });

  it('replaces the release offer with the reload prompt and restores it when deferred', async () => {
    auth.signedIn = true;
    route.name = 'map';
    const updates = useReleaseUpdatesStore();
    vi.spyOn(updates, 'startChecks').mockReturnValue(vi.fn());
    const view = notice(false);
    updates.status = { latestReleaseVersion: '0.9.0', installedReleaseVersion: '0.8.1' } as typeof updates.status;
    updates.noticeVisible = true;
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()[0].textContent).toContain('MTL Explorer 0.9.0 is available');
    await view.setProps({ browserUpdateAvailable: true });
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer update ready');
    await view.setProps({ browserUpdateAvailable: false });
    expect(cards()[0].textContent).toContain('View update');
    (cards()[0].querySelector('button') as HTMLButtonElement).click();
    await view.vm.$nextTick();
    expect(updates.detailsVisible).toBe(true);
    expect(cards()).toHaveLength(0);
  });

  it('shows installed releases as success notices and removes them on dismissal and logout', async () => {
    auth.signedIn = true;
    route.name = 'map';
    const updates = useReleaseUpdatesStore();
    vi.spyOn(updates, 'startChecks').mockReturnValue(vi.fn());
    const view = notice(false);
    updates.status = { installedReleaseVersion: '0.9.0' } as typeof updates.status;
    updates.installedNoticeVisible = true;
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()[0].classList.contains('notification-card--success')).toBe(true);
    expect(cards()[0].textContent).toContain('Updated to MTL Explorer 0.9.0');
    (cards()[0].querySelectorAll('button')[1] as HTMLButtonElement).click();
    await view.vm.$nextTick();
    expect(cards()).toHaveLength(0);
    updates.installedNoticeVisible = true;
    await view.vm.$nextTick();
    auth.signedIn = false;
    route.name = 'login';
    await view.vm.$nextTick();
    expect(cards()).toHaveLength(0);
  });

  it('keeps the release completion through Reload and replaces the generic confirmation', async () => {
    auth.signedIn = true;
    route.name = 'map';
    const offer = {
      installationId: 'installation-a',
      channel: 'stable',
      status: 'UPDATE_AVAILABLE',
      installedBuildId: '1.508',
      installedReleaseVersion: '0.8.1',
      latestBuildId: '1.520',
      latestReleaseVersion: '0.9.0',
    } as ReleaseUpdateResponse;
    const installed = {
      ...offer,
      status: 'UP_TO_DATE',
      installedBuildId: '1.520',
      installedReleaseVersion: '0.9.0',
    } as ReleaseUpdateResponse;
    const updates = useReleaseUpdatesStore();
    vi.spyOn(updates, 'startChecks').mockReturnValue(vi.fn());
    const oldPage = notice(true);
    updates.applyStatus(offer);
    updates.applyStatus(installed);
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('Reload');
    (cards()[0].querySelector('button') as HTMLButtonElement).click();
    expect(oldPage.emitted('reload')).toHaveLength(1);
    oldPage.unmount();

    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    vi.spyOn(reopened, 'startChecks').mockReturnValue(vi.fn());
    notice(false, true);
    await vi.advanceTimersByTimeAsync(2500);
    reopened.applyStatus(installed);
    await vi.advanceTimersByTimeAsync(0);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('Updated to MTL Explorer 0.9.0');
    expect(cards()[0].textContent).toContain('What’s new');
    (cards()[0].querySelectorAll('button')[1] as HTMLButtonElement).click();
    await vi.advanceTimersByTimeAsync(0);
    expect(cards()).toHaveLength(0);

    setActivePinia(createPinia());
    const acknowledged = useReleaseUpdatesStore();
    acknowledged.applyStatus(installed);
    expect(acknowledged.installedNoticeVisible).toBe(false);
  });

  it('retains the timed interface confirmation when no release completion is known', async () => {
    notice(false, true);
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer updated');
    await vi.advanceTimersByTimeAsync(1000);
    cards()[0].parentElement?.dispatchEvent(new MouseEvent('mouseenter'));
    await vi.advanceTimersByTimeAsync(5000);
    expect(cards()).toHaveLength(1);
    cards()[0].parentElement?.dispatchEvent(new MouseEvent('mouseleave'));
    await vi.advanceTimersByTimeAsync(4000);
    expect(cards()).toHaveLength(0);
  });

  it('keeps an offer pending across reloads when the interface confirmation hides it', async () => {
    auth.signedIn = true;
    route.name = 'map';
    const updates = useReleaseUpdatesStore();
    vi.spyOn(updates, 'startChecks').mockReturnValue(vi.fn());
    const view = notice(false, true);
    updates.applyStatus(availableRelease());
    await vi.advanceTimersByTimeAsync(2500);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer updated');
    expect(noticedRelease()).toBeUndefined();
    view.unmount();

    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    reopened.applyStatus(availableRelease());
    expect(reopened.noticeVisible).toBe(true);
  });

  it('acknowledges an offer only after the interface confirmation expires', async () => {
    auth.signedIn = true;
    route.name = 'map';
    const updates = useReleaseUpdatesStore();
    vi.spyOn(updates, 'startChecks').mockReturnValue(vi.fn());
    notice(false, true);
    updates.applyStatus(availableRelease());
    await vi.advanceTimersByTimeAsync(2500);
    await vi.advanceTimersByTimeAsync(4999);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer updated');
    expect(noticedRelease()).toBeUndefined();

    await vi.advanceTimersByTimeAsync(1);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer 0.9.0 is available');
    expect(noticedRelease()).toBe('stable:0.9.0:1.520');

    updates.applyStatus(availableRelease('0.10.0', '1.530'));
    await vi.advanceTimersByTimeAsync(0);
    expect(cards()).toHaveLength(1);
    expect(cards()[0].textContent).toContain('MTL Explorer 0.10.0 is available');
    expect(noticedRelease()).toBe('stable:0.10.0:1.530');
  });
});
