<template>
  <span class="segment-reference-mark" :style="{ '--reference-color': color }" aria-hidden="true"><span>R</span></span>
</template>

<script setup lang="ts">
defineOptions({ name: 'SegmentReferenceMark' });
defineProps<{ color: string }>();
</script>

<style scoped>
.segment-reference-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 0.95rem;
  height: 0.95rem;
  margin: 0 0.1rem;
  border: 2px solid var(--reference-color);
  border-radius: 2px;
  background: #fff;
  transform: rotate(45deg);
  vertical-align: middle;
}
.segment-reference-mark span {
  transform: rotate(-45deg);
  color: var(--reference-color);
  font-size: 0.55rem;
  font-weight: 800;
  line-height: 1;
}
</style>
