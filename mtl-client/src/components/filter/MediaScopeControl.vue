<template>
  <div class="media-scope-control">
    <div class="media-scope-control__row">
      <span>Media</span>
      <div class="view-toggle" role="group" aria-label="Media scope">
        <button
          v-for="option in options"
          :key="option.value"
          type="button"
          class="view-toggle-button"
          :class="{ 'view-toggle-button--active': store.scope === option.value }"
          :aria-pressed="store.scope === option.value"
          :aria-describedby="helpId"
          @click="store.scope = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </div>
    <p :id="helpId">
      From the first matching track’s start to the last matching track’s end. Undated media is excluded.
      {{
        store.scope === MediaTrendRequestScopeEnum.AllIndexed
          ? 'Includes media without a track link.'
          : 'Only media linked to matching tracks.'
      }}
      Applies to the map and statistics.
    </p>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue';
import { MediaTrendRequestScopeEnum } from 'x8ing-mtl-api-typescript-fetch';
import { useMediaFilterStore } from '@/stores/mediaFilterStore';
const store = useMediaFilterStore();
const helpId = useId();
const options = [
  { value: MediaTrendRequestScopeEnum.AllIndexed, label: 'All media' },
  { value: MediaTrendRequestScopeEnum.MatchedActivities, label: 'Track related' },
];
</script>

<style scoped>
.media-scope-control {
  padding: 0.6rem 0;
}
.media-scope-control__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}
.media-scope-control p {
  margin: 0.4rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
</style>
