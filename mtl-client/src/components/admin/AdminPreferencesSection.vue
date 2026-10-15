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
            class="segmented-control"
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
      <div class="settings-field admin-preference-block">
        <div class="admin-action-copy">
          <label id="admin-format-locale-label" for="admin-format-locale" class="settings-field__label"
            >Format locale</label
          >
          <span class="admin-action-hint">This changes formatting without changing the interface language.</span>
        </div>
        <Select
          v-model="localeModel"
          input-id="admin-format-locale"
          aria-labelledby="admin-format-locale-label"
          :options="localePresets"
          option-label="label"
          option-value="value"
          placeholder="Browser default"
          class="admin-preference-select"
        />
        <code class="settings-preview">Preview: {{ localePreview }}</code>
        <span class="settings-field__hint">
          Detected {{ localeDetection.value || 'no matching preset' }} from {{ localeDetection.browserLang }} and
          {{ localeDetection.timezone }}.
        </span>
      </div>
      <div class="settings-field admin-preference-block">
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
</style>
