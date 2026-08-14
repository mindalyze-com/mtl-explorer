<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Preferences"
      description="Local appearance, formatting, and measurement settings for this browser."
      icon="bi bi-sliders"
    />

    <section class="admin-card" aria-labelledby="admin-appearance-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-appearance-heading">Appearance</h3>
          <p>Changes apply immediately and are saved on this device.</p>
        </div>
      </div>
      <div class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Color scheme</span>
          <span class="admin-action-hint">Choose the light or dark application interface.</span>
        </div>
        <div class="admin-action-controls">
          <SelectButton
            v-model="colorScheme"
            :options="schemeOptions"
            option-label="label"
            option-value="value"
            :allow-empty="false"
            aria-label="Color scheme"
          />
        </div>
      </div>
    </section>

    <section class="admin-card" aria-labelledby="admin-region-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-region-heading">Region &amp; units</h3>
          <p>Control number, date, time, distance, speed, and weight formatting.</p>
        </div>
      </div>
      <div class="admin-preference-block">
        <div class="admin-action-copy">
          <span class="admin-action-label">Format locale</span>
          <span class="admin-action-hint">This changes formatting without changing the interface language.</span>
        </div>
        <Select
          v-model="localeModel"
          :options="localePresets"
          option-label="label"
          option-value="value"
          placeholder="Browser default"
          class="admin-preference-select"
        />
        <code class="admin-preference-preview">Preview: {{ localePreview }}</code>
        <span class="admin-preference-caption">
          Detected {{ localeDetection.value || 'no matching preset' }} from {{ localeDetection.browserLang }} and
          {{ localeDetection.timezone }}.
        </span>
      </div>
      <div class="admin-preference-block">
        <MeasurementUnitsSetting />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Select from 'primevue/select';
import SelectButton from 'primevue/selectbutton';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import MeasurementUnitsSetting from '@/components/admin/MeasurementUnitsSetting.vue';
import { useTheme } from '@/composables/useTheme';
import { detectBestLocale, LOCALE_PRESETS, useLocale } from '@/composables/useLocale';

defineOptions({ name: 'AdminPreferencesSection' });

const { colorScheme: themeColorScheme, setScheme } = useTheme();
const colorScheme = computed({
  get: () => themeColorScheme.value,
  set: (value) => setScheme(value),
});
const schemeOptions = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
];

const { formatLocale, setLocale } = useLocale();
const localeModel = computed({
  get: () => formatLocale.value,
  set: (value) => setLocale(value),
});
const localePresets = [...LOCALE_PRESETS];
const localeDetection = detectBestLocale();
const localePreview = computed(() => {
  const locale = formatLocale.value || undefined;
  const now = new Date();
  const date = now.toLocaleDateString(locale, { year: 'numeric', month: '2-digit', day: '2-digit' });
  const time = now.toLocaleTimeString(locale, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const number = (12345.67).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${date} ${time} — ${number}`;
});
</script>

<style scoped>
.admin-preference-block {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.8rem 0;
  border-top: 1px solid var(--border-subtle);
}

.admin-preference-block:first-of-type {
  padding-top: 0;
  border-top: 0;
}

.admin-preference-block:last-child {
  padding-bottom: 0;
}

.admin-preference-select {
  width: min(100%, 25rem);
}

.admin-preference-preview {
  display: block;
  width: fit-content;
  max-width: 100%;
  padding: 0.45rem 0.6rem;
  border-radius: 0.4rem;
  background: var(--code-bg);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  overflow-wrap: anywhere;
}

.admin-preference-caption {
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  line-height: 1.4;
}
</style>
