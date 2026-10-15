import { onScopeDispose, watch } from 'vue';
import type { RouteLocationNormalizedLoaded, RouteRecordNameGeneric, Router } from 'vue-router';
import { warnRequestFailure } from '@/utils/safeLogging';

type MapTrackRoute = {
  fullPath: string;
  name: RouteRecordNameGeneric | null;
  params: RouteLocationNormalizedLoaded['params'];
};
type TrackRouteState = {
  interactionId: number | null;
  presentation: string | null;
  selectedKey: string | null;
  detailKind: string | null;
  trackId: number | null;
  visible: boolean;
  toolActive: boolean;
};
type ReturnContext = {
  interactionId: number;
  returnPath: string;
  trackId: number | null;
  selectedKey: string | null;
  hasHistoryEntry: boolean;
};

/** The map caller owns URLs; retained click results and target detail components remain unchanged. */
export function useMapInteractionTrackRoute(options: {
  router: Router;
  state: () => TrackRouteState;
  showChooser: () => void;
  restoreSelected: () => void;
  fallbackReturnPath: () => string;
  rememberStandaloneReturn: (path: string) => void;
}) {
  const { router } = options;
  let context: ReturnContext | null = null;
  let ignoredInteraction: number | null = null;
  let navigationRevision = 0;
  let pendingPath: string | null = null;
  let pendingOwner: { interactionId: number | null; selectedKey: string | null } | null = null;
  let pendingHistoryBack = false;
  let pendingHistoryOrigin: string | null = null;
  let applyingPath: string | null = null;

  const isGpsView = (state: TrackRouteState) =>
    state.presentation === 'target' && state.detailKind === 'track' && state.visible && state.trackId != null;
  const trackPath = (trackId: number) => router.resolve({ name: 'track-detail', params: { id: trackId } }).fullPath;

  function navigate(path: string, replace = false, force = false) {
    const revision = ++navigationRevision;
    const owner = context ? { interactionId: context.interactionId, selectedKey: context.selectedKey } : null;
    const originPath = router.currentRoute.value.fullPath;
    pendingPath = path;
    pendingOwner = owner;
    pendingHistoryBack = false;
    pendingHistoryOrigin = null;
    function recoverRejectedNavigation() {
      const state = options.state();
      if (
        revision === navigationRevision &&
        owner &&
        context?.interactionId === owner.interactionId &&
        context.selectedKey === owner.selectedKey &&
        state.interactionId === owner.interactionId &&
        state.selectedKey === owner.selectedKey &&
        isGpsView(state) &&
        path === trackPath(state.trackId!) &&
        router.currentRoute.value.fullPath === originPath &&
        (router.currentRoute.value.fullPath !== path || router.options.history.location !== path)
      )
        options.showChooser();
    }
    void router[replace ? 'replace' : 'push'](force ? { path, force: true } : path)
      .then((failure) => {
        if (failure) recoverRejectedNavigation();
      })
      .finally(() => {
        if (revision === navigationRevision) {
          pendingPath = null;
          pendingOwner = null;
          pendingHistoryBack = false;
          pendingHistoryOrigin = null;
        }
      })
      .catch((error: unknown) => {
        if (revision === navigationRevision && applyingPath === path) applyingPath = null;
        recoverRejectedNavigation();
        warnRequestFailure('Could not update the map track URL', error, {
          topic1: 'navigation',
          topic2: 'map',
          topic3: 'trackRoute',
        });
      });
  }

  function returnToMap(retained: ReturnContext, preserveForward: boolean) {
    const current = router.currentRoute.value;
    if (pendingPath === retained.returnPath) return;
    if (current.name !== 'track-detail' && pendingPath == null) return;
    if (
      preserveForward &&
      retained.hasHistoryEntry &&
      current.name === 'track-detail' &&
      router.options.history.location === current.fullPath
    ) {
      navigationRevision++;
      pendingPath = retained.returnPath;
      pendingOwner = { interactionId: retained.interactionId, selectedKey: retained.selectedKey };
      pendingHistoryBack = true;
      pendingHistoryOrigin = current.fullPath;
      router.back();
    } else {
      // Replacing even the current map URL cancels a still-pending GPS push.
      navigate(retained.returnPath, true);
    }
  }

  function handleRouteChange(to: MapTrackRoute, from?: MapTrackRoute): boolean {
    if (applyingPath === to.fullPath) applyingPath = null;
    const retained = context;
    if (!retained || options.state().interactionId !== retained.interactionId) return false;
    if (to.fullPath === retained.returnPath) {
      if (from?.name === 'track-detail' && options.state().presentation === 'target' && isGpsView(options.state()))
        options.showChooser();
      return true;
    }
    if (
      to.name === 'track-detail' &&
      Number(to.params.id) === retained.trackId &&
      options.state().selectedKey === retained.selectedKey &&
      options.state().detailKind === 'track'
    ) {
      if (options.state().presentation === 'chooser') options.restoreSelected();
      if (from?.fullPath === retained.returnPath) retained.hasHistoryEntry = true;
      return true;
    }
    ignoredInteraction = retained.interactionId;
    context = null;
    return false;
  }

  // Guards validate stale caller navigation only. UI changes wait for a committed route.
  const removeGuard = router.beforeEach((to) => {
    if (pendingPath === to.fullPath && to.name === 'track-detail') {
      const state = options.state();
      if (
        !state.visible ||
        Number(to.params.id) !== state.trackId ||
        (context ? state.interactionId !== context.interactionId || !isGpsView(state) : state.interactionId != null)
      )
        return false;
    }
    applyingPath = to.fullPath;
  });
  const removeAfter = router.afterEach((to, from, failure) => {
    if ((failure || to.fullPath === from.fullPath) && applyingPath === to.fullPath) applyingPath = null;
    if (pendingPath === to.fullPath && (!failure || pendingHistoryBack)) {
      pendingPath = null;
      pendingOwner = null;
      pendingHistoryBack = false;
      pendingHistoryOrigin = null;
    }
    if (
      failure &&
      context?.returnPath === to.fullPath &&
      options.state().presentation === 'chooser' &&
      options.state().selectedKey === context.selectedKey &&
      router.currentRoute.value.name === 'track-detail' &&
      Number(router.currentRoute.value.params.id) === context.trackId
    )
      options.restoreSelected();
  });
  const removeError = router.onError((error, to, from) => {
    if (!pendingHistoryBack || pendingPath !== to.fullPath || pendingHistoryOrigin !== from.fullPath) return;
    const owner = pendingOwner;
    pendingPath = null;
    pendingOwner = null;
    pendingHistoryBack = false;
    pendingHistoryOrigin = null;
    if (applyingPath === to.fullPath) applyingPath = null;
    const state = options.state();
    if (
      owner &&
      context?.interactionId === owner.interactionId &&
      context.selectedKey === owner.selectedKey &&
      state.interactionId === owner.interactionId &&
      state.selectedKey === owner.selectedKey &&
      state.presentation === 'chooser' &&
      router.currentRoute.value.name === 'track-detail' &&
      Number(router.currentRoute.value.params.id) === context.trackId
    )
      options.restoreSelected();
    warnRequestFailure('Could not return from the map track URL', error, {
      topic1: 'navigation',
      topic2: 'map',
      topic3: 'trackRoute',
    });
  });

  watch(
    () => [options.state(), router.currentRoute.value.fullPath] as const,
    ([state]) => {
      // The caller's route handler consumes commits before reconciling local detail state.
      if (applyingPath === router.currentRoute.value.fullPath) return;
      if (state.interactionId !== ignoredInteraction) ignoredInteraction = null;
      if (state.interactionId == null || state.interactionId === ignoredInteraction) {
        const previous = context;
        context = null;
        if (previous && !state.toolActive) {
          if (state.visible && state.trackId != null) {
            options.rememberStandaloneReturn(previous.returnPath);
            const path = trackPath(state.trackId);
            if (router.currentRoute.value.fullPath !== path) navigate(path);
          } else returnToMap(previous, true);
        }
        return;
      }
      if (context?.interactionId !== state.interactionId) {
        const current = router.currentRoute.value;
        context = {
          interactionId: state.interactionId,
          returnPath:
            current.name === 'track-detail' ? (context?.returnPath ?? options.fallbackReturnPath()) : current.fullPath,
          trackId: null,
          selectedKey: null,
          hasHistoryEntry: current.name === 'track-detail' && (context?.hasHistoryEntry ?? false),
        };
      }
      if (isGpsView(state)) {
        const retained = context;
        const path = trackPath(state.trackId!);
        retained.trackId = state.trackId;
        retained.selectedKey = state.selectedKey;
        const supersedesOwnedRequest =
          pendingPath === path &&
          pendingOwner != null &&
          (pendingOwner.interactionId !== retained.interactionId || pendingOwner.selectedKey !== state.selectedKey);
        const supersedesHistoryBack =
          pendingHistoryBack &&
          pendingOwner != null &&
          (pendingOwner.interactionId !== retained.interactionId || pendingOwner.selectedKey !== state.selectedKey);
        const replacesCurrentEntry =
          router.currentRoute.value.name === 'track-detail' &&
          router.options.history.location === router.currentRoute.value.fullPath;
        if (supersedesHistoryBack) {
          // A pop changes browser history before its guards finish. Preserve the actual map entry.
          navigate(path, replacesCurrentEntry, true);
        } else if (
          router.currentRoute.value.fullPath !== path &&
          (pendingPath !== path || supersedesOwnedRequest) &&
          (applyingPath !== path || supersedesOwnedRequest)
        ) {
          navigate(path, replacesCurrentEntry);
        }
      } else {
        if (state.presentation === 'target' && state.selectedKey !== context.selectedKey) {
          context.trackId = null;
          context.selectedKey = null;
        }
        const supersedesHistoryBack =
          pendingHistoryBack &&
          pendingOwner != null &&
          (pendingOwner.interactionId !== context.interactionId || pendingOwner.selectedKey !== state.selectedKey);
        if (supersedesHistoryBack) navigate(context.returnPath, true);
        else if (pendingPath !== context.returnPath && applyingPath !== context.returnPath)
          returnToMap(context, state.presentation === 'chooser');
      }
    },
    { immediate: true, flush: 'post' }
  );

  function backToResults(): boolean {
    const retained = context;
    if (!retained || options.state().interactionId !== retained.interactionId || !isGpsView(options.state()))
      return false;
    // Committed GPS history navigates first; an aborted Back leaves its details visible.
    if (router.currentRoute.value.name !== 'track-detail') options.showChooser();
    returnToMap(retained, true);
    return true;
  }

  function exitToMap(): void {
    ignoredInteraction = options.state().interactionId;
    context = null;
    navigate(router.resolve({ name: 'home' }).fullPath, true);
  }

  onScopeDispose(() => {
    removeGuard();
    removeAfter();
    removeError();
    navigationRevision++;
  });
  return { handleRouteChange, backToResults, exitToMap, isManagingRoute: () => context != null || pendingPath != null };
}
