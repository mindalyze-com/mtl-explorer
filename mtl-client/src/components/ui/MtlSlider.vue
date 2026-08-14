<template>
  <div
    ref="rootEl"
    class="mtl-slider"
    :class="[`mtl-slider--${variant}`, { 'mtl-slider--disabled': disabled, 'mtl-slider--range': range }]"
    @pointerdown="onTrackPointerDown"
  >
    <div class="mtl-slider__inner">
      <div ref="trackEl" class="mtl-slider__track" aria-hidden="true">
        <div v-if="variant === 'opacity'" class="mtl-slider__checker"></div>
        <div v-if="variant === 'opacity'" class="mtl-slider__opacity-gradient"></div>
        <div class="mtl-slider__range" :style="rangeStyle"></div>
      </div>

      <span
        v-for="handle in handles"
        :key="handle.index"
        class="mtl-slider__handle"
        :class="{ 'mtl-slider__handle--active': activeHandle === handle.index }"
        :style="{ left: `${handle.percent}%` }"
        :tabindex="disabled ? -1 : 0"
        role="slider"
        :aria-valuemin="handle.ariaMin"
        :aria-valuenow="handle.value"
        :aria-valuemax="handle.ariaMax"
        :aria-valuetext="handle.ariaValueText"
        :aria-label="handle.ariaLabel"
        :aria-labelledby="ariaLabelledby || undefined"
        :aria-disabled="disabled ? 'true' : undefined"
        @pointerdown.stop="onHandlePointerDown($event, handle.index)"
        @keydown="onHandleKeyDown($event, handle.index)"
      ></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';

type SliderValue = number | number[];
type SliderVariant = 'default' | 'opacity';
type HandleIndex = 0 | 1;

const props = withDefaults(
  defineProps<{
    modelValue: SliderValue;
    min?: number;
    max?: number;
    step?: number;
    range?: boolean;
    disabled?: boolean;
    ariaLabel?: string;
    ariaLabelledby?: string;
    ariaValueText?: string | string[];
    variant?: SliderVariant;
  }>(),
  {
    min: 0,
    max: 100,
    step: 1,
    range: false,
    disabled: false,
    ariaLabel: undefined,
    ariaLabelledby: undefined,
    ariaValueText: undefined,
    variant: 'default',
  }
);

const emit = defineEmits<{
  'update:modelValue': [value: SliderValue];
  change: [value: SliderValue];
  slideend: [event: { originalEvent: PointerEvent; value: SliderValue }];
}>();

const rootEl = ref<HTMLElement | null>(null);
const trackEl = ref<HTMLElement | null>(null);
const activeHandle = ref<HandleIndex | null>(null);
const lastInteractionValue = ref<SliderValue | null>(null);
let lastPointerEvent: PointerEvent | null = null;

const minValue = computed(() => Math.min(toFiniteNumber(props.min, 0), toFiniteNumber(props.max, 100)));
const maxValue = computed(() => Math.max(toFiniteNumber(props.min, 0), toFiniteNumber(props.max, 100)));
const span = computed(() => Math.max(maxValue.value - minValue.value, Number.EPSILON));
const normalizedStep = computed(() => {
  const step = toFiniteNumber(props.step, 1);
  return step > 0 ? step : 1;
});

const singleValue = computed(() => {
  const raw = Array.isArray(props.modelValue) ? props.modelValue[0] : props.modelValue;
  return normalizeValue(raw, minValue.value);
});

const rangeValue = computed<[number, number]>(() => {
  const raw = Array.isArray(props.modelValue)
    ? props.modelValue
    : [minValue.value, toFiniteNumber(props.modelValue, minValue.value)];
  const first = normalizeValue(raw[0], minValue.value);
  const second = normalizeValue(raw[1], maxValue.value);
  return first <= second ? [first, second] : [second, first];
});

const outputValue = computed<SliderValue>(() => (props.range ? [...rangeValue.value] : singleValue.value));

const rangeStyle = computed(() => {
  if (props.range) {
    const [start, end] = rangeValue.value;
    const left = percentForValue(start);
    const right = percentForValue(end);
    return { left: `${left}%`, width: `${right - left}%` };
  }

  return { left: '0%', width: `${percentForValue(singleValue.value)}%` };
});

const handles = computed(() => {
  if (props.range) {
    const [start, end] = rangeValue.value;
    return [
      {
        index: 0 as HandleIndex,
        value: start,
        percent: percentForValue(start),
        ariaMin: minValue.value,
        ariaMax: end,
        ariaLabel: rangeHandleLabel('minimum'),
        ariaValueText: valueTextForHandle(0),
      },
      {
        index: 1 as HandleIndex,
        value: end,
        percent: percentForValue(end),
        ariaMin: start,
        ariaMax: maxValue.value,
        ariaLabel: rangeHandleLabel('maximum'),
        ariaValueText: valueTextForHandle(1),
      },
    ];
  }

  return [
    {
      index: 0 as HandleIndex,
      value: singleValue.value,
      percent: percentForValue(singleValue.value),
      ariaMin: minValue.value,
      ariaMax: maxValue.value,
      ariaLabel: props.ariaLabel,
      ariaValueText: valueTextForHandle(0),
    },
  ];
});

function toFiniteNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function decimalPlaces(value: number): number {
  const [, decimal = ''] = String(value).split('.');
  return decimal.length;
}

function normalizeValue(value: unknown, fallback: number): number {
  const raw = toFiniteNumber(value, fallback);
  const clamped = Math.max(minValue.value, Math.min(maxValue.value, raw));
  const step = normalizedStep.value;
  const stepped = Math.round((clamped - minValue.value) / step) * step + minValue.value;
  const precision = Math.min(decimalPlaces(step) + 2, 10);
  return Math.max(minValue.value, Math.min(maxValue.value, Number(stepped.toFixed(precision))));
}

function percentForValue(value: number): number {
  return ((value - minValue.value) / span.value) * 100;
}

function valueFromPointer(event: PointerEvent): number {
  const rect = trackEl.value?.getBoundingClientRect();
  if (!rect || rect.width <= 0) return singleValue.value;
  const percent = (event.clientX - rect.left) / rect.width;
  return normalizeValue(minValue.value + percent * span.value, minValue.value);
}

function rangeHandleLabel(kind: 'minimum' | 'maximum'): string | undefined {
  if (!props.ariaLabel) return undefined;
  return `${props.ariaLabel} ${kind}`;
}

function valueTextForHandle(index: HandleIndex): string | undefined {
  return Array.isArray(props.ariaValueText) ? props.ariaValueText[index] : props.ariaValueText;
}

function nearestHandleIndex(value: number): HandleIndex {
  if (!props.range) return 0;
  const [start, end] = rangeValue.value;
  const startDistance = Math.abs(value - start);
  const endDistance = Math.abs(value - end);
  if (startDistance === endDistance) {
    return value <= (start + end) / 2 ? 0 : 1;
  }
  return startDistance < endDistance ? 0 : 1;
}

function copyValue(value: SliderValue): SliderValue {
  return Array.isArray(value) ? [...value] : value;
}

function valuesEqual(left: SliderValue, right: SliderValue): boolean {
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
    return left.every((value, index) => value === right[index]);
  }
  return left === right;
}

function emitValue(value: SliderValue) {
  const nextValue = copyValue(value);
  const currentValue = lastInteractionValue.value ?? outputValue.value;
  if (valuesEqual(nextValue, currentValue)) return;

  lastInteractionValue.value = copyValue(nextValue);
  emit('update:modelValue', nextValue);
  emit('change', nextValue);
}

function updateHandle(index: HandleIndex, rawValue: number) {
  if (props.disabled) return;

  if (props.range) {
    const next: [number, number] = [...rangeValue.value];
    if (index === 0) {
      next[0] = Math.min(normalizeValue(rawValue, next[0]), next[1]);
    } else {
      next[1] = Math.max(normalizeValue(rawValue, next[1]), next[0]);
    }
    emitValue(next);
    return;
  }

  emitValue(normalizeValue(rawValue, singleValue.value));
}

function startDrag(event: PointerEvent, explicitIndex?: HandleIndex) {
  if (props.disabled) return;

  event.preventDefault();
  lastInteractionValue.value = null;
  lastPointerEvent = event;
  const pointerValue = valueFromPointer(event);
  activeHandle.value = explicitIndex ?? nearestHandleIndex(pointerValue);
  updateHandle(activeHandle.value, pointerValue);

  rootEl.value?.setPointerCapture?.(event.pointerId);
  window.addEventListener('pointermove', onPointerMove, { passive: false });
  window.addEventListener('pointerup', onPointerEnd);
  window.addEventListener('pointercancel', onPointerEnd);
}

function onTrackPointerDown(event: PointerEvent) {
  startDrag(event);
}

function onHandlePointerDown(event: PointerEvent, index: HandleIndex) {
  startDrag(event, index);
}

function onPointerMove(event: PointerEvent) {
  if (activeHandle.value == null) return;
  event.preventDefault();
  lastPointerEvent = event;
  updateHandle(activeHandle.value, valueFromPointer(event));
}

function onPointerEnd(event: PointerEvent) {
  removePointerListeners();
  rootEl.value?.releasePointerCapture?.(event.pointerId);
  const value = lastInteractionValue.value ?? outputValue.value;
  activeHandle.value = null;
  emit('slideend', { originalEvent: event, value: copyValue(value) });
  lastInteractionValue.value = null;
  lastPointerEvent = null;
}

function removePointerListeners() {
  window.removeEventListener('pointermove', onPointerMove);
  window.removeEventListener('pointerup', onPointerEnd);
  window.removeEventListener('pointercancel', onPointerEnd);
}

function onHandleKeyDown(event: KeyboardEvent, index: HandleIndex) {
  if (props.disabled) return;

  const multiplier = event.shiftKey ? 10 : 1;
  let nextValue: number | null = null;
  const current = props.range ? rangeValue.value[index] : singleValue.value;

  switch (event.code) {
    case 'ArrowDown':
    case 'ArrowLeft':
      nextValue = current - normalizedStep.value * multiplier;
      break;
    case 'ArrowUp':
    case 'ArrowRight':
      nextValue = current + normalizedStep.value * multiplier;
      break;
    case 'PageDown':
      nextValue = current - normalizedStep.value * 10;
      break;
    case 'PageUp':
      nextValue = current + normalizedStep.value * 10;
      break;
    case 'Home':
      nextValue = minValue.value;
      break;
    case 'End':
      nextValue = maxValue.value;
      break;
    default:
      return;
  }

  event.preventDefault();
  lastInteractionValue.value = null;
  activeHandle.value = index;
  updateHandle(index, nextValue);
  activeHandle.value = null;
  lastInteractionValue.value = null;
}

onBeforeUnmount(() => {
  removePointerListeners();
  if (lastPointerEvent?.pointerId != null) {
    rootEl.value?.releasePointerCapture?.(lastPointerEvent.pointerId);
  }
});
</script>

<style scoped>
.mtl-slider {
  --mtl-slider-track-height: var(--mtl-slider-track-height-default, 8px);
  --mtl-slider-handle-size: var(--mtl-slider-handle-size-default, 22px);
  --mtl-slider-hit-padding-y: 10px;
  --mtl-slider-hit-padding-x: 0;
  --mtl-slider-track-background: var(--slider-track);
  --mtl-slider-range-background: var(--slider-gradient);
  --mtl-slider-handle-background: var(--slider-handle);
  --mtl-slider-handle-border-color: var(--slider-handle-border, var(--accent));
  --mtl-slider-handle-border-width: 2px;
  --mtl-slider-handle-halo: 0 0 0 0 transparent;
  --mtl-slider-handle-halo-active: 0 0 0 5px var(--accent-subtle);
  --mtl-slider-track-border: none;
  --mtl-slider-opacity-checker-base: rgba(200, 200, 200, 0.2);
  --mtl-slider-opacity-checker-color: rgba(140, 140, 140, 0.18);
  --mtl-slider-opacity-checker-size: 8px;
  --mtl-slider-opacity-checker-offset: calc(var(--mtl-slider-opacity-checker-size) / 2);
  position: relative;
  width: 100%;
  min-width: 0;
  padding: var(--mtl-slider-hit-padding-y) var(--mtl-slider-hit-padding-x);
  box-sizing: border-box;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

.mtl-slider--disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.mtl-slider__inner {
  position: relative;
  min-width: 0;
}

.mtl-slider__track {
  position: relative;
  height: var(--mtl-slider-track-height);
  overflow: hidden;
  border: var(--mtl-slider-track-border);
  border-radius: 999px;
  background: var(--mtl-slider-track-background);
}

.mtl-slider__range {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: inherit;
  background: var(--mtl-slider-range-background);
}

.mtl-slider__checker,
.mtl-slider__opacity-gradient {
  position: absolute;
  inset: 0;
}

.mtl-slider__checker {
  background-color: var(--mtl-slider-opacity-checker-base);
  background-image:
    linear-gradient(45deg, var(--mtl-slider-opacity-checker-color) 25%, transparent 25%),
    linear-gradient(-45deg, var(--mtl-slider-opacity-checker-color) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, var(--mtl-slider-opacity-checker-color) 75%),
    linear-gradient(-45deg, transparent 75%, var(--mtl-slider-opacity-checker-color) 75%);
  background-size: var(--mtl-slider-opacity-checker-size) var(--mtl-slider-opacity-checker-size);
  background-position:
    0 0,
    0 var(--mtl-slider-opacity-checker-offset),
    var(--mtl-slider-opacity-checker-offset) calc(0px - var(--mtl-slider-opacity-checker-offset)),
    calc(0px - var(--mtl-slider-opacity-checker-offset)) 0;
}

.mtl-slider__opacity-gradient {
  background: var(
    --mtl-slider-opacity-gradient,
    linear-gradient(to right, transparent, var(--mtl-slider-accent, var(--accent)))
  );
}

.mtl-slider--opacity {
  --mtl-slider-track-height-default: 10px;
  --mtl-slider-track-height-coarse: 12px;
  --mtl-slider-track-border: 1px solid var(--border-default);
  --mtl-slider-handle-border-color: var(--mtl-slider-accent, var(--accent));
  --mtl-slider-handle-border-width: 2.5px;
}

.mtl-slider--opacity .mtl-slider__range {
  display: none;
}

.mtl-slider__handle {
  position: absolute;
  top: 50%;
  z-index: 1;
  width: var(--mtl-slider-handle-size);
  height: var(--mtl-slider-handle-size);
  border: var(--mtl-slider-handle-border-width) solid var(--mtl-slider-handle-border-color);
  border-radius: 50%;
  background: var(--mtl-slider-handle-background);
  box-shadow:
    0 1px 5px rgba(0, 0, 0, 0.18),
    var(--mtl-slider-handle-halo);
  transform: translate(-50%, -50%);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.mtl-slider__handle:hover,
.mtl-slider__handle:focus-visible,
.mtl-slider__handle--active {
  box-shadow:
    0 1px 5px rgba(0, 0, 0, 0.18),
    var(--mtl-slider-handle-halo-active);
  outline: none;
}

.mtl-slider__handle--active {
  transform: translate(-50%, -50%) scale(1.02);
}

.mtl-slider--disabled .mtl-slider__handle {
  pointer-events: none;
}

@media (pointer: coarse) {
  .mtl-slider {
    --mtl-slider-track-height: var(--mtl-slider-track-height-coarse, 10px);
    --mtl-slider-handle-size: var(--mtl-slider-handle-size-coarse, 28px);
    --mtl-slider-hit-padding-y: var(--mtl-slider-hit-padding-y-coarse, 14px);
  }

  .mtl-slider--opacity {
    --mtl-slider-handle-border-width: 3px;
  }
}
</style>
