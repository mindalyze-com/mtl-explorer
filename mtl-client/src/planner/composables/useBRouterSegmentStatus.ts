import { computed, isRef, onBeforeUnmount, watch, type Ref } from 'vue';
import { SIDECAR_STATUS_POLL_MS } from '@/planner/constants/PlannerConstants';
import {
  APPLICATION_STATUS_SECTION,
  type ApplicationStatusPollingConsumer,
  useApplicationStatusStore,
} from '@/stores/applicationStatusStore';

/**
 * Poll BRouter sidecar status while `enabled` is true.
 *
 * `enabled` may be a static boolean or a reactive ref. When it's a ref,
 * polling starts/stops as the ref flips, so callers can gate on e.g.
 * whether the planner sheet is open — avoiding background polling when
 * the planner isn't visible.
 */
export function useBRouterSegmentStatus(enabled: boolean | Ref<boolean> = true) {
  const applicationStatusStore = useApplicationStatusStore();
  const status = computed(() => applicationStatusStore.services?.planner ?? null);
  const loading = computed(() => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES].loading);
  let pollingConsumer: ApplicationStatusPollingConsumer | null = null;

  async function refresh(force = false) {
    if (force) await applicationStatusStore.force(APPLICATION_STATUS_SECTION.SERVICES);
    else await applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.SERVICES]);
  }

  function start() {
    if (pollingConsumer) return;
    pollingConsumer = applicationStatusStore.registerPollingConsumer({
      [APPLICATION_STATUS_SECTION.SERVICES]: SIDECAR_STATUS_POLL_MS,
    });
  }

  function stop() {
    pollingConsumer?.dispose();
    pollingConsumer = null;
  }

  if (isRef(enabled)) {
    watch(
      enabled,
      (on) => {
        if (on) start();
        else stop();
      },
      { immediate: true }
    );
  } else if (enabled) {
    start();
  }

  onBeforeUnmount(stop);

  return { status, loading, refresh };
}
