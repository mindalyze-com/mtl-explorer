<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import pLimit from 'p-limit';
import { TracksControllerApi, type GpsTrack, type ReviewedTrack } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { reviewError } from './api';

const props = defineProps<{ active: boolean; tracks: ReviewedTrack[] }>();
const METADATA_CONCURRENCY = 4;
const metadata = ref<Record<number, GpsTrack>>({});
const expanded = ref<Record<number, boolean>>({});
const loading = ref(false);
const error = ref('');
let request: AbortController | undefined;

async function load() {
  request?.abort();
  metadata.value = {};
  expanded.value = {};
  error.value = '';
  loading.value = false;
  if (!props.active || !props.tracks.length) return;
  const current = new AbortController();
  request = current;
  loading.value = true;
  const api = new TracksControllerApi(getApiConfiguration());
  const limit = pLimit(METADATA_CONCURRENCY);
  await Promise.all(
    props.tracks.map((track) =>
      limit(async () => {
        if (current.signal.aborted || track.trackId == null) return;
        try {
          const result = await api.getSingleTrack(
            { gpsTrackId: track.trackId, includeGeometry: false },
            { signal: current.signal, cache: 'no-cache' }
          );
          if (!current.signal.aborted) metadata.value[track.trackId] = result;
        } catch (failure) {
          if (!current.signal.aborted) {
            const message = await reviewError(failure);
            if (!current.signal.aborted) error.value = message;
          }
        }
      })
    )
  );
  if (!current.signal.aborted) loading.value = false;
}
function name(track: ReviewedTrack) {
  const info = track.trackId == null ? undefined : metadata.value[track.trackId];
  return info?.trackName || info?.metaName || track.name || 'Unnamed track';
}
function description(trackId: number) {
  const info = metadata.value[trackId];
  return info?.trackDescription || info?.metaDescription || '';
}
function startDate(trackId: number) {
  const date = metadata.value[trackId]?.startDate;
  return date && Number.isFinite(date.getTime()) ? date.toLocaleString() : 'Unavailable';
}
watch([() => props.active, () => props.tracks.map((track) => track.trackId).join(',')], load, { immediate: true });
onBeforeUnmount(() => request?.abort());
</script>

<template>
  <section v-if="tracks.length" class="source-file-info" aria-label="File info">
    <h3>File info</h3>
    <p v-if="loading" class="source-file-info__status" role="status">Loading GPS details…</p>
    <div v-if="error" class="source-file-info__status" role="alert">
      Some GPS details could not be loaded. <button type="button" :title="error" @click="load">Retry</button>
    </div>
    <dl v-for="(track, index) in tracks" :key="track.trackId ?? index">
      <dt>Name</dt>
      <dd class="source-file-info__name" :title="name(track)">{{ name(track) }}</dd>
      <dt>Start date</dt>
      <dd>{{ track.trackId == null || (loading && !metadata[track.trackId]) ? '—' : startDate(track.trackId) }}</dd>
      <template v-if="track.trackId != null && description(track.trackId)">
        <dt>Description</dt>
        <dd>
          <button
            type="button"
            class="source-file-info__description"
            :class="{ 'source-file-info__description--expanded': expanded[track.trackId] }"
            :aria-expanded="!!expanded[track.trackId]"
            :title="description(track.trackId)"
            @click="expanded[track.trackId] = !expanded[track.trackId]"
          >
            {{ description(track.trackId) }}
          </button>
        </dd>
      </template>
    </dl>
  </section>
</template>

<style scoped>
.source-file-info {
  margin: 0.75rem 0;
  font-size: var(--text-sm-size);
}
h3 {
  margin: 0 0 0.5rem;
  font-size: inherit;
}
dl {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.25rem 0.75rem;
  margin: 0.5rem 0;
}
dt,
.source-file-info__status {
  color: var(--text-secondary);
}
dt {
  align-self: center;
}
dd {
  min-width: 0;
  margin: 0;
}
.source-file-info__name,
.source-file-info__description {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-file-info__description {
  display: block;
  width: 100%;
  min-height: 2.75rem;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.source-file-info__description--expanded {
  white-space: normal;
  overflow-wrap: anywhere;
}
button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
