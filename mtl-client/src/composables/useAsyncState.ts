import { ref, type Ref } from 'vue';

export function useAsyncState<ErrorValue>(initialError: ErrorValue) {
  const loading = ref(false);
  const error = ref(initialError) as Ref<ErrorValue>;

  function begin(): void {
    loading.value = true;
    error.value = initialError;
  }

  function finish(): void {
    loading.value = false;
  }

  function resetError(): void {
    error.value = initialError;
  }

  return { loading, error, begin, finish, resetError };
}
