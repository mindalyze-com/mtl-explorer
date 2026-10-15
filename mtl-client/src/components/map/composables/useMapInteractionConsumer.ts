import { inject, onBeforeUnmount, type InjectionKey } from 'vue';
import type { MapInteractionConsumer } from '../mapInteractionCoordinator';

export type RegisterMapInteractionConsumer = (consumer: MapInteractionConsumer) => () => void;
export const MAP_INTERACTION_REGISTRATION_KEY: InjectionKey<RegisterMapInteractionConsumer> =
  Symbol('map-interaction-registration');

/** Map children register their collector/provider and release it with their component. */
export function useMapInteractionConsumer(consumer: MapInteractionConsumer): () => void {
  const register = inject(MAP_INTERACTION_REGISTRATION_KEY);
  if (!register) throw new Error('Map interaction dispatcher is not available');
  const cleanup = register(consumer);
  onBeforeUnmount(cleanup);
  return cleanup;
}
