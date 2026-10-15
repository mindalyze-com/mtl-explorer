<template>
  <div class="admin-page admin-overview">
    <AdminSectionHeader
      title="Overview"
      description="Preferences, connected services, and tools for this installation."
      icon="bi bi-grid-1x2"
    />

    <details class="admin-disclosure admin-status-summary" :open="attentionCount > 0">
      <summary>
        <span class="admin-status-summary__copy">
          <strong>System status</strong>
          <span :class="{ 'admin-status-summary__attention': attentionCount > 0 }">{{ statusSummary }}</span>
        </span>
      </summary>
      <div class="admin-disclosure__body">
        <div class="admin-card__header">
          <p>Current processing, data, services, and connections.</p>
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
        <div class="settings-list">
          <button
            v-for="card in cards"
            :key="card.id"
            type="button"
            :class="['settings-row settings-row--stacked-value', `admin-status-row--${card.tone}`]"
            @click="$emit('navigate', card.section)"
          >
            <span class="settings-row__icon"><i :class="card.icon" aria-hidden="true" /></span>
            <span class="settings-row__body">
              <strong>{{ card.label }}</strong>
              <small>{{ card.detail }}</small>
            </span>
            <span class="settings-row__value">{{ card.value }}</span>
            <i class="bi bi-chevron-right settings-row__chevron" aria-hidden="true" />
          </button>
        </div>
      </div>
    </details>

    <nav class="admin-section-index" aria-label="All settings">
      <section
        v-for="group in groups"
        :key="group.id"
        class="admin-section-index__group"
        :aria-labelledby="`admin-index-${group.id}`"
      >
        <div class="settings-section-heading">
          <h3 :id="`admin-index-${group.id}`">{{ group.label }}</h3>
        </div>
        <div class="settings-list settings-list--panel">
          <button
            v-for="section in sectionsForGroup(group.id)"
            :key="section.id"
            type="button"
            class="settings-row settings-row--navigation"
            @click="$emit('navigate', section.id)"
          >
            <span class="settings-row__icon"><i :class="section.icon" aria-hidden="true" /></span>
            <span class="settings-row__body">
              <strong>{{ section.label }}</strong>
              <small>{{ section.description }}</small>
            </span>
            <span
              v-if="badges[section.id]"
              :class="['admin-nav-badge', { 'admin-nav-badge--live': badges[section.id] === 'Live' }]"
              >{{ badges[section.id] }}</span
            >
            <i class="bi bi-chevron-right settings-row__chevron" aria-hidden="true" />
          </button>
        </div>
      </section>
      <button type="button" class="settings-row settings-row--navigation" @click="$emit('show-about')">
        <span class="settings-row__icon"><i class="bi bi-book" aria-hidden="true" /></span>
        <span class="settings-row__body"><strong>About &amp; credits</strong></span>
        <i class="bi bi-chevron-right settings-row__chevron" aria-hidden="true" />
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Button from 'primevue/button';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import {
  ADMIN_SECTION_GROUPS,
  sectionsForGroup,
  type AdminOverviewStatus,
  type AdminSectionId,
} from '@/components/admin/adminSections';

defineOptions({ name: 'AdminOverview' });

const props = withDefaults(
  defineProps<{
    cards: AdminOverviewStatus[];
    badges?: Partial<Record<AdminSectionId, string>>;
    refreshing?: boolean;
  }>(),
  { badges: () => ({}), refreshing: false }
);

defineEmits<{
  (event: 'navigate', section: AdminSectionId): void;
  (event: 'refresh'): void;
  (event: 'show-about'): void;
}>();

const groups = ADMIN_SECTION_GROUPS;
const attentionCount = computed(() => props.cards.filter((card) => ['warning', 'error'].includes(card.tone)).length);
const statusSummary = computed(() => {
  if (attentionCount.value)
    return `${attentionCount.value} ${attentionCount.value === 1 ? 'item needs' : 'items need'} attention`;
  if (props.refreshing || !props.cards.length || props.cards.some((card) => card.value === 'Checking'))
    return 'Checking status…';
  if (props.cards.some((card) => card.tone === 'live')) return 'Background work is active';
  return 'View processing and connection status';
});
</script>
