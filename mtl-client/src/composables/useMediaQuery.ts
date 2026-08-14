import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue';

export function useMediaQuery(query: string, fallback = false): Readonly<Ref<boolean>> {
  const mediaQuery = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(query) : null;
  const matches = ref(mediaQuery?.matches ?? fallback);

  function update(event: MediaQueryListEvent): void {
    matches.value = event.matches;
  }

  onMounted(() => mediaQuery?.addEventListener('change', update));
  onBeforeUnmount(() => mediaQuery?.removeEventListener('change', update));

  return matches;
}
