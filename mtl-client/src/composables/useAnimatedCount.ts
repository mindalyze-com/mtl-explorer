import { onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter } from 'vue';
import {
  COUNT_PLAYBACK_DELAY_MS,
  COUNTER_RENDER_INTERVAL_MS,
  countAnimationHasPendingChanges,
  countAnimationValueAt,
  latestCountSample,
  observeCountAnimation,
  type CountAnimation,
} from '@/utils/countAnimation';

interface AnimatedCountOptions {
  sample?: MaybeRefOrGetter<unknown>;
  resetKey?: MaybeRefOrGetter<unknown>;
  resetOnDecrease?: MaybeRefOrGetter<boolean | undefined>;
  /** Null means the shared source has no sample yet; undefined uses a local timeline. */
  animation?: MaybeRefOrGetter<CountAnimation | null | undefined>;
}

/** Render timestamped samples at the current simulation time, without extrapolation. */
export function useAnimatedCount(
  value: MaybeRefOrGetter<number | null | undefined>,
  options: AnimatedCountOptions = {}
) {
  const displayValue = ref<number | null>(null);
  const latestValue = ref<number | null>(null);
  const isAnimating = ref(false);
  const reducedMotion =
    typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let currentAnimation: CountAnimation | undefined;
  let skipHistoryBefore: number | undefined;

  function stop() {
    clearTimeout(timer);
    timer = undefined;
    isAnimating.value = false;
  }

  function render() {
    stop();
    const animation = currentAnimation;
    if (!animation) {
      displayValue.value = null;
      return;
    }
    if (document.visibilityState === 'hidden') return;
    const latest = latestCountSample(animation);
    if (reducedMotion?.matches || animation.samples.length === 1) {
      displayValue.value = latest.value;
      skipHistoryBefore = latest.sampledAt;
      return;
    }
    const tick = () => {
      const now = Date.now();
      // A count already shown with reduced motion must not replay older history.
      const evaluationTime = Math.max(now, (skipHistoryBefore ?? -Infinity) + COUNT_PLAYBACK_DELAY_MS);
      displayValue.value = countAnimationValueAt(animation, evaluationTime);
      isAnimating.value = countAnimationHasPendingChanges(animation, evaluationTime);
      timer = isAnimating.value ? setTimeout(tick, COUNTER_RENDER_INTERVAL_MS) : undefined;
    };
    tick();
  }

  function motionChanged() {
    render();
  }
  reducedMotion?.addEventListener('change', motionChanged);

  function visibilityChanged() {
    if (document.visibilityState === 'hidden') stop();
    else render();
  }
  document.addEventListener('visibilitychange', visibilityChanged);

  watch(
    () => [toValue(value), toValue(options.sample), toValue(options.resetKey), toValue(options.animation)] as const,
    ([reportedValue, , key, sharedAnimation]) => {
      const animation =
        sharedAnimation === undefined
          ? observeCountAnimation(currentAnimation, reportedValue, Date.now(), {
              resetKey: key,
              resetOnDecrease: toValue(options.resetOnDecrease),
              animate: !reducedMotion?.matches,
            })
          : (sharedAnimation ?? undefined);
      latestValue.value = animation ? latestCountSample(animation).value : null;
      if (animation === currentAnimation) return;
      if (!animation || animation.resetKey !== currentAnimation?.resetKey || animation.samples.length === 1) {
        skipHistoryBefore = undefined;
      }
      currentAnimation = animation;
      render();
    },
    { immediate: true }
  );

  onScopeDispose(() => {
    stop();
    reducedMotion?.removeEventListener('change', motionChanged);
    document.removeEventListener('visibilitychange', visibilityChanged);
  });
  return { displayValue, latestValue, isAnimating };
}
