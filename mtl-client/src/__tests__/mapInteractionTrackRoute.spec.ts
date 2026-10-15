import { flushPromises } from '@vue/test-utils';
import { effectScope, shallowRef, watch } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useMapInteractionTrackRoute } from '@/components/map/composables/useMapInteractionTrackRoute';
import { warnRequestFailure } from '@/utils/safeLogging';

vi.mock('@/utils/safeLogging', () => ({ warnRequestFailure: vi.fn() }));
const scopes: ReturnType<typeof effectScope>[] = [];
afterEach(() => {
  for (const scope of scopes.splice(0)) scope.stop();
  vi.clearAllMocks();
});

async function routeFixture() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: {} },
      { path: '/track/:id', name: 'track-detail', component: {} },
    ],
  });
  router.onError(vi.fn());
  await router.push('/');
  const state = shallowRef({
    interactionId: 12,
    presentation: 'chooser',
    selectedKey: 'track:1',
    detailKind: 'track',
    trackId: 1,
    visible: true,
    toolActive: false,
  });
  const showChooser = vi.fn(() => {
    state.value = { ...state.value, presentation: 'chooser' };
  });
  const scope = effectScope();
  scopes.push(scope);
  let bridge!: ReturnType<typeof useMapInteractionTrackRoute>;
  scope.run(() => {
    bridge = useMapInteractionTrackRoute({
      router,
      state: () => state.value,
      showChooser,
      restoreSelected: () => {
        state.value = { ...state.value, presentation: 'target' };
      },
      fallbackReturnPath: () => '/',
      rememberStandaloneReturn: vi.fn(),
    });
    watch(
      () => router.currentRoute.value.fullPath,
      (path, from) => {
        bridge.handleRouteChange(router.resolve(path), from ? router.resolve(from) : undefined);
      },
      { immediate: true }
    );
  });
  await flushPromises();
  return { router, state, showChooser, bridge };
}

describe('rejected map GPS URL navigation', () => {
  it.each([1, 2])(
    'does not let a pending older Back hide a new click to GPS track %i or lose its actual history entry',
    async (trackId) => {
      const { router, state, bridge } = await routeFixture();
      state.value = { ...state.value, presentation: 'target' };
      await flushPromises();
      let releaseBack!: (allowed: boolean) => void;
      const removeGuard = router.beforeEach((to) =>
        to.name === 'home'
          ? new Promise<boolean>((resolve) => {
              releaseBack = resolve;
            })
          : undefined
      );
      bridge.backToResults();
      await flushPromises();
      state.value = { ...state.value, interactionId: 13, trackId, selectedKey: `track:${trackId}` };
      await flushPromises();
      releaseBack(true);
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe(`/track/${trackId}`);
      expect(router.options.history.location).toBe(`/track/${trackId}`);
      expect(state.value).toMatchObject({ interactionId: 13, presentation: 'target', selectedKey: `track:${trackId}` });
      removeGuard();
      bridge.backToResults();
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/');
      expect(router.options.history.location).toBe('/');
      expect(state.value.presentation).toBe('chooser');
      router.forward();
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe(`/track/${trackId}`);
      expect(state.value.presentation).toBe('target');
    }
  );

  it('keeps GPS details aligned after a thrown owned Back and permits retry after the guard is removed', async () => {
    const { router, state, showChooser, bridge } = await routeFixture();
    state.value = { ...state.value, presentation: 'target' };
    await flushPromises();
    const removeGuard = router.beforeEach((to) => {
      if (to.name === 'home') throw new Error('Back guard failed');
    });
    bridge.backToResults();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(state.value.presentation).toBe('target');
    expect(showChooser).not.toHaveBeenCalled();
    removeGuard();
    bridge.backToResults();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(state.value.presentation).toBe('chooser');
    expect(warnRequestFailure).toHaveBeenCalledOnce();
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(state.value.presentation).toBe('target');
  });

  it.each(['rejected', 'thrown'])(
    'restores the preserved chooser when an owned GPS replacement is %s at an older track URL',
    async (kind) => {
      const { router, state, showChooser } = await routeFixture();
      state.value = { ...state.value, presentation: 'target' };
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/track/1');
      router.beforeEach((to) => {
        if (to.fullPath !== '/track/2') return;
        if (kind === 'thrown') throw new Error('Navigation guard failed');
        return false;
      });
      state.value = { ...state.value, interactionId: 13, trackId: 2, selectedKey: 'track:2' };
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/');
      expect(state.value).toMatchObject({
        interactionId: 13,
        trackId: 2,
        selectedKey: 'track:2',
        presentation: 'chooser',
        visible: true,
      });
      expect(showChooser).toHaveBeenCalledOnce();
      if (kind === 'thrown') expect(warnRequestFailure).toHaveBeenCalledOnce();
    }
  );

  it('restores the preserved chooser when the first GPS navigation throws at the original map URL', async () => {
    const { router, state, showChooser } = await routeFixture();
    const removeGuard = router.beforeEach((to) => {
      if (to.name === 'track-detail') throw new Error('Navigation guard failed');
    });
    state.value = { ...state.value, presentation: 'target' };
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(state.value.presentation).toBe('chooser');
    expect(state.value.trackId).toBe(1);
    expect(showChooser).toHaveBeenCalledOnce();
    expect(warnRequestFailure).toHaveBeenCalledOnce();
    removeGuard();
    state.value = { ...state.value, presentation: 'target' };
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(state.value.presentation).toBe('target');
  });

  it('ignores a superseded navigation throwing after GPS 2 has been selected and committed', async () => {
    const { router, state, showChooser } = await routeFixture();
    let rejectOld!: (error: Error) => void;
    router.beforeEach((to) =>
      to.fullPath === '/track/1'
        ? new Promise((_resolve, reject) => {
            rejectOld = reject;
          })
        : undefined
    );
    state.value = { ...state.value, presentation: 'target' };
    await flushPromises();
    state.value = { ...state.value, interactionId: 13, trackId: 2, selectedKey: 'track:2' };
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/2');
    rejectOld(new Error('Old navigation failed'));
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/2');
    expect(state.value).toMatchObject({ interactionId: 13, trackId: 2, presentation: 'target' });
    expect(showChooser).not.toHaveBeenCalled();
  });
});
