import { isRef, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { SidecarStatus } from '@/planner/types';
import { fetchSidecarStatus } from '@/planner/repositories/plannerRepository';
import { SIDECAR_STATUS_POLL_MS } from '@/planner/constants/PlannerConstants';

/**
 * Poll BRouter sidecar status while `enabled` is true.
 *
 * `enabled` may be a static boolean or a reactive ref. When it's a ref,
 * polling starts/stops as the ref flips, so callers can gate on e.g.
 * whether the planner sheet is open — avoiding background polling when
 * the planner isn't visible.
 */
export function useBRouterSegmentStatus(enabled: boolean | Ref<boolean> = true) {
  const status = ref<SidecarStatus | null>(null);
  const loading = ref(false);
  let timer: number | null = null;

  async function refresh() {
    loading.value = true;
    try {
      status.value = await fetchSidecarStatus();
    } finally {
      loading.value = false;
    }
  }

  function start() {
    if (timer !== null) return;
    void refresh();
    timer = window.setInterval(() => { void refresh(); }, SIDECAR_STATUS_POLL_MS);
  }

  function stop() {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  if (isRef(enabled)) {
    watch(enabled, (on) => { on ? start() : stop(); }, { immediate: true });
  } else if (enabled) {
    start();
  }

  onBeforeUnmount(stop);

  return { status, loading, refresh };
}
