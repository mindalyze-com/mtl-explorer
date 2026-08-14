<template>
  <div class="admin-page admin-overview">
    <AdminSectionHeader title="Overview" description="System health and common admin tasks." icon="bi bi-grid-1x2" />

    <section aria-labelledby="admin-health-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-health-heading">Status</h3>
          <p>Current processing, data, service, and helper state.</p>
        </div>
        <Button
          label="Refresh"
          icon="pi pi-refresh"
          size="small"
          severity="secondary"
          :loading="refreshing"
          :disabled="refreshing"
          @click="$emit('refresh')"
        />
      </div>
      <div class="admin-overview-grid">
        <button
          v-for="card in cards"
          :key="card.id"
          type="button"
          :class="['admin-overview-card', `admin-overview-card--${card.tone}`]"
          @click="$emit('navigate', card.section)"
        >
          <span class="admin-overview-card__icon"><i :class="card.icon" /></span>
          <span class="admin-overview-card__copy">
            <span class="admin-overview-card__label">{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <span>{{ card.detail }}</span>
          </span>
          <i class="bi bi-chevron-right admin-overview-card__arrow" aria-hidden="true" />
        </button>
      </div>
    </section>

    <section class="admin-card" aria-labelledby="admin-shortcuts-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-shortcuts-heading">Common tasks</h3>
          <p>Open a section before running an action.</p>
        </div>
      </div>
      <div class="admin-shortcuts">
        <button type="button" class="admin-shortcut" @click="$emit('navigate', 'imports')">
          <i class="bi bi-file-earmark-arrow-up" />
          <span><strong>Import files</strong><small>Upload GPX, FIT, TCX, and other track formats.</small></span>
          <i class="bi bi-chevron-right" />
        </button>
        <button type="button" class="admin-shortcut" @click="$emit('navigate', 'imports')">
          <i class="bi bi-cloud-arrow-down" />
          <span><strong>Garmin sync</strong><small>Start the configured remote export job.</small></span>
          <i class="bi bi-chevron-right" />
        </button>
        <button type="button" class="admin-shortcut" @click="$emit('navigate', 'maintenance')">
          <i class="bi bi-arrow-repeat" />
          <span><strong>Maintenance</strong><small>Reload tracks, rescan files, or manage helper tools.</small></span>
          <i class="bi bi-chevron-right" />
        </button>
      </div>
    </section>

    <nav class="admin-mobile-index" aria-label="Admin sections">
      <section v-for="group in groups" :key="group.id" class="admin-mobile-index__group">
        <span class="admin-mobile-index__label">{{ group.label }}</span>
        <button
          v-for="section in sectionsForGroup(group.id)"
          :key="section.id"
          type="button"
          class="admin-mobile-section"
          @click="$emit('navigate', section.id)"
        >
          <i :class="section.icon" aria-hidden="true" />
          <span class="admin-mobile-section__copy">
            <strong>{{ section.label }}</strong>
            <span>{{ section.description }}</span>
          </span>
          <span v-if="badges[section.id]" class="admin-nav-badge">{{ badges[section.id] }}</span>
          <i v-else class="bi bi-chevron-right" aria-hidden="true" />
        </button>
      </section>
      <button type="button" class="admin-mobile-about" @click="$emit('show-about')">
        <i class="bi bi-book" />
        <span>About &amp; credits</span>
        <i class="bi bi-chevron-right" />
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import Button from 'primevue/button';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import {
  ADMIN_SECTION_GROUPS,
  sectionsForGroup,
  type AdminOverviewStatus,
  type AdminSectionId,
} from '@/components/admin/adminSections';

defineOptions({ name: 'AdminOverview' });

withDefaults(
  defineProps<{
    cards: AdminOverviewStatus[];
    badges?: Partial<Record<AdminSectionId, string>>;
    refreshing?: boolean;
  }>(),
  {
    badges: () => ({}),
    refreshing: false,
  }
);

defineEmits<{
  (event: 'navigate', section: AdminSectionId): void;
  (event: 'refresh'): void;
  (event: 'show-about'): void;
}>();

const groups = ADMIN_SECTION_GROUPS;
</script>

<style scoped>
.admin-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.admin-overview-card {
  display: grid;
  grid-template-columns: 2.25rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.65rem;
  min-height: 6.4rem;
  padding: 0.8rem;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
  background: var(--surface-glass-light);
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.admin-overview-card:hover {
  border-color: var(--border-hover);
  background: var(--surface-hover);
}

.admin-overview-card--live {
  border-color: color-mix(in srgb, var(--accent) 32%, var(--border-default));
}

.admin-overview-card--warning {
  border-color: color-mix(in srgb, var(--warning) 40%, var(--border-default));
}

.admin-overview-card--error {
  border-color: color-mix(in srgb, var(--error) 38%, var(--border-default));
}

.admin-overview-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 0.5rem;
  background: var(--surface-elevated);
  color: var(--accent-text);
}

.admin-overview-card__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.15rem;
}

.admin-overview-card__label {
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.admin-overview-card__copy strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.admin-overview-card__copy span:last-child {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: 1.35;
}

.admin-overview-card__arrow {
  color: var(--text-faint);
}

.admin-shortcuts {
  display: flex;
  flex-direction: column;
}

.admin-shortcut {
  display: grid;
  grid-template-columns: 1.75rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.6rem;
  min-height: 3.6rem;
  padding: 0.55rem 0;
  border: 0;
  border-top: 1px solid var(--border-subtle);
  background: transparent;
  color: var(--accent-text);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.admin-shortcut:first-child {
  border-top: 0;
}

.admin-shortcut > span {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}

.admin-shortcut strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.admin-shortcut small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: 1.35;
}

.admin-mobile-about {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  min-height: 3.5rem;
  padding: 0.65rem 0.8rem;
  border: 0;
  border-top: 1px solid var(--border-default);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

@media (max-width: 768px) {
  .admin-overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
