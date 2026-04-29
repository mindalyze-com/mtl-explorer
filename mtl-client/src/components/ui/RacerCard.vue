<template>
  <div
    class="rc-card"
    :class="{
      'rc-card--highlighted': highlighted,
      'rc-card--clickable': clickable,
    }"
  >
    <!-- Left: medal+rank (when ranked) or colored dot -->
    <div class="rc-swatch-col">
      <template v-if="rank != null">
        <i class="bi bi-award-fill rc-medal" :style="{ color }"></i>
        <span class="rc-rank">{{ rank }}</span>
      </template>
      <span v-else class="rc-dot" :style="{ backgroundColor: color }"></span>
    </div>

    <!-- Right: body -->
    <div class="rc-body">
      <div class="rc-main">
        <div class="rc-info">
          <span class="rc-name-row">
            <span class="rc-name">{{ name }}</span>
            <ActivityTypeBadge
              v-if="activityType"
              :type="activityType"
              size="xs"
              class="rc-activity"
            />
          </span>
          <span class="rc-date">{{ dateStr }}</span>
        </div>
        <button
          class="rc-open"
          @click.stop="$emit('open-details', trackId)"
          title="Open track details"
        >
          <i class="bi bi-box-arrow-up-right"></i>
        </button>
      </div>
      <div v-if="stats && stats.length > 0" class="rc-stats">
        <span
          v-for="(s, i) in stats"
          :key="i"
          class="rc-stat"
          :title="s.title || ''"
        >
          <i class="bi" :class="s.icon"></i> {{ s.text }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

defineProps({
  color:        { type: String,  required: true },
  name:         { type: String,  required: true },
  dateStr:      { type: String,  default: '' },
  trackId:      { required: true },
  stats:        { type: Array,   default: () => [] },
  rank:         { type: Number,  default: null },
  activityType: { type: String,  default: null },
  highlighted:  { type: Boolean, default: false },
  clickable:    { type: Boolean, default: false },
});

defineEmits(['open-details']);
</script>

<style scoped>
.rc-card {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--surface-glass);
  border: 1px solid var(--border-medium);
  cursor: default;
  transition: background 0.15s, border-color 0.15s;
}

.rc-card--clickable {
  cursor: pointer;
}

.rc-card:hover {
  background: var(--surface-hover);
  border-color: var(--border-light);
}

.rc-card--highlighted {
  border-color: var(--accent-text);
  background: color-mix(in srgb, var(--accent-text) 8%, var(--surface-glass));
}

/* Left column */
.rc-swatch-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

.rc-medal {
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  flex-shrink: 0;
}

.rc-rank {
  font-size: var(--text-2xs-size);
  font-weight: 600;
  color: var(--text-muted);
  line-height: var(--text-2xs-lh);
  letter-spacing: 0.02em;
}

.rc-dot {
  width: 0.85rem;
  height: 0.85rem;
  border-radius: 50%;
  flex-shrink: 0;
}

/* Body */
.rc-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1 1 auto;
  min-width: 0;
}

.rc-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.rc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1 1 auto;
}

.rc-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.rc-name {
  font-size: var(--text-sm-size);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
  flex: 1 1 auto;
}

.rc-activity {
  flex: 0 0 auto;
  transform: scale(0.85);
  transform-origin: right center;
}

.rc-date {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}

/* Open button */
.rc-open {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  flex-shrink: 0;
  align-self: flex-start;
  margin-left: auto;
  margin-top: -5px;
  margin-right: -5px;
  padding: 0.45rem 0.5rem;
  min-width: 2rem;
  min-height: 2rem;
  font-size: var(--text-sm-size);
  border-radius: 6px;
  line-height: var(--text-sm-lh);
  display: flex;
  align-items: center;
  justify-content: center;
}

.rc-open:hover {
  color: var(--accent-text);
  background: color-mix(in srgb, var(--accent-text) 10%, transparent);
}

/* Stats row */
.rc-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-left: 0;
}

.rc-stat {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.rc-stat i {
  font-size: var(--text-xs-size);
  opacity: 0.7;
}
</style>
