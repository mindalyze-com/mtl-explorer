import { onBeforeUnmount, watch } from 'vue';
import type { Map as MapLibreMap } from 'maplibre-gl';
import type { BottomSheetLayoutState } from '@/components/ui/BottomSheet.vue';
import type { MapInteractionState } from '../mapInteractionCoordinator';

export const MAP_TARGET_CHOOSER_DETENT_ID = 'choice';
export const MAP_INTERACTION_REVEAL_SETTLE_MS = 100;
const REVEAL_MARGIN_PX = 32;
const CLICK_MARKER_RADIUS_PX = 7;
const REVEAL_DURATION_MS = 180;

/** Reveal an occluded click once, using the shared sheet's actual viewport geometry. */
export function useMapInteractionVisibility(options: {
  map: () => MapLibreMap | undefined;
  interaction: () => MapInteractionState | null;
}) {
  let layout: BottomSheetLayoutState | null = null;
  let handledId: number | null = null;
  let pending: ReturnType<typeof setTimeout> | null = null;

  function cancelPending() {
    if (pending !== null) clearTimeout(pending);
    pending = null;
  }

  function scheduleReveal() {
    cancelPending();
    const state = options.interaction();
    if (
      !state ||
      state.presentation !== 'chooser' ||
      handledId === state.id ||
      !layout?.open ||
      layout.dragging ||
      layout.widthPx <= 0 ||
      layout.heightPx <= 0 ||
      layout.detentId !== MAP_TARGET_CHOOSER_DETENT_ID
    )
      return;
    const id = state.id;
    pending = setTimeout(() => {
      pending = null;
      const current = options.interaction();
      const map = options.map();
      if (!current || current.id !== id || current.presentation !== 'chooser' || !map || !layout?.open) return;
      handledId = id;
      if (layout.fullscreen || layout.dragging) return;

      const rect = map.getContainer().getBoundingClientRect();
      const point = map.project(current.context.lngLat);
      const x = rect.left + point.x;
      const y = rect.top + point.y;
      const targetY = Math.min(rect.bottom, layout.topPx) - REVEAL_MARGIN_PX;
      if (
        rect.width <= 0 ||
        rect.height <= 0 ||
        !Number.isFinite(x) ||
        !Number.isFinite(y) ||
        targetY < rect.top + REVEAL_MARGIN_PX ||
        x < rect.left ||
        x > rect.right ||
        y < rect.top ||
        y > rect.bottom ||
        x < layout.leftPx - CLICK_MARKER_RADIUS_PX ||
        x > layout.rightPx + CLICK_MARKER_RADIUS_PX ||
        y < layout.topPx - CLICK_MARKER_RADIUS_PX ||
        y <= targetY ||
        y > layout.bottomPx + CLICK_MARKER_RADIUS_PX
      )
        return;

      // A pixel pan keeps zoom, bearing and pitch. No originalEvent: discovery stays active.
      map.panBy([0, y - targetY], { duration: REVEAL_DURATION_MS });
    }, MAP_INTERACTION_REVEAL_SETTLE_MS);
  }

  function onLayoutChange(value: BottomSheetLayoutState) {
    const state = options.interaction();
    if (
      state?.presentation === 'chooser' &&
      value.open &&
      (value.dragging ||
        value.fullscreen ||
        (pending !== null &&
          layout?.detentId === MAP_TARGET_CHOOSER_DETENT_ID &&
          value.detentId !== MAP_TARGET_CHOOSER_DETENT_ID))
    ) {
      handledId = state.id;
      cancelPending();
    }
    layout = value;
    scheduleReveal();
  }

  function onManualMove(event: { originalEvent?: Event }) {
    if (!event.originalEvent) return;
    handledId = options.interaction()?.id ?? null;
    cancelPending();
  }

  watch(
    options.map,
    (map, previous) => {
      previous?.off('movestart', onManualMove);
      map?.on('movestart', onManualMove);
      cancelPending();
      handledId = null;
      scheduleReveal();
    },
    { immediate: true, flush: 'sync' }
  );
  watch(
    () => [options.interaction()?.id, options.interaction()?.presentation] as const,
    ([id, presentation], previous) => {
      if (id !== previous?.[0]) {
        cancelPending();
        handledId = null;
        scheduleReveal();
      } else if (presentation !== previous?.[1] && presentation !== 'chooser') {
        handledId = id ?? null;
        cancelPending();
      }
    },
    { flush: 'sync' }
  );
  onBeforeUnmount(() => {
    cancelPending();
    options.map()?.off('movestart', onManualMove);
  });

  return { onLayoutChange };
}
