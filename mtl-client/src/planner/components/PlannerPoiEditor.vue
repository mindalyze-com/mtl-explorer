<template>
  <PrimeDialog
    :visible="point !== null"
    modal
    :header="point?.id ? 'Edit point of interest' : 'Add point of interest'"
    :style="{ width: 'min(440px, 92vw)' }"
    :draggable="false"
    :close-button-props="{ autofocus: false }"
    :pt="{ transition: { onAfterEnter: focusDialogInput } }"
    @update:visible="emit('cancel')"
  >
    <form class="poi-form" @submit.prevent="confirm">
      <label class="poi-field">
        <span>Name</span>
        <InputText v-model="name" :maxlength="POINT_OF_INTEREST_NAME_MAX_LENGTH" />
      </label>
      <div class="poi-field">
        <span>Icon</span>
        <PrimeSelect
          v-model="iconType"
          :options="PLANNER_POI_ICON_OPTIONS"
          option-label="label"
          option-value="value"
          aria-label="Icon"
        >
          <template #value="{ value }">
            <span class="poi-icon-option"
              ><i :class="`bi bi-${poiIconOption(value).icon}`" aria-hidden="true" />{{
                poiIconOption(value).label
              }}</span
            >
          </template>
          <template #option="{ option }">
            <span class="poi-icon-option"
              ><i :class="`bi bi-${option.icon}`" aria-hidden="true" />{{ option.label }}</span
            >
          </template>
        </PrimeSelect>
      </div>
      <label class="poi-field">
        <span>Notes (optional)</span>
        <PrimeTextarea v-model="notes" rows="3" auto-resize :maxlength="POINT_OF_INTEREST_NOTES_MAX_LENGTH" />
      </label>
      <p class="poi-coordinate">{{ point?.lat.toFixed(6) }}, {{ point?.lng.toFixed(6) }}</p>
      <p class="poi-note">Saved with this plan. Not included in GPX exports.</p>
      <div class="poi-actions">
        <PrimeButton label="Cancel" severity="secondary" text @click="emit('cancel')" />
        <PrimeButton type="submit" :label="point?.id ? 'Apply changes' : 'Add POI'" :disabled="!name.trim()" />
      </div>
    </form>
  </PrimeDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import PrimeDialog from 'primevue/dialog';
import PrimeButton from 'primevue/button';
import InputText from 'primevue/inputtext';
import PrimeTextarea from 'primevue/textarea';
import PrimeSelect from 'primevue/select';
import type { PointOfInterest } from '@/planner/types';
import { focusDialogInput } from '@/utils/dialogFocus';
import {
  DEFAULT_PLANNER_POI_ICON_TYPE,
  PLANNER_POI_ICON_OPTIONS,
  poiIconOption,
  type PlannerPoiIconType,
} from '@/planner/utils/plannerPoiIconTypes';
import {
  POINT_OF_INTEREST_NAME_MAX_LENGTH,
  POINT_OF_INTEREST_NOTES_MAX_LENGTH,
} from '@/planner/constants/PlannerConstants';

const props = defineProps<{ point: PointOfInterest | null }>();
const emit = defineEmits<{ save: [point: PointOfInterest]; cancel: [] }>();
const name = ref('');
const notes = ref('');
const iconType = ref<PlannerPoiIconType>(DEFAULT_PLANNER_POI_ICON_TYPE);
watch(
  () => props.point,
  (point) => {
    name.value = point?.name ?? '';
    notes.value = point?.notes ?? '';
    iconType.value = poiIconOption(point?.iconType).value;
  },
  { immediate: true }
);
function confirm() {
  if (!props.point || !name.value.trim()) return;
  emit('save', {
    ...props.point,
    name: name.value.trim(),
    iconType: iconType.value,
    notes: notes.value.trim() || undefined,
  });
}
</script>

<style scoped>
.poi-form,
.poi-field {
  display: grid;
  gap: 0.5rem;
}
.poi-form {
  gap: 0.85rem;
}
.poi-field,
.poi-note,
.poi-coordinate {
  font-size: var(--text-sm-size);
}
.poi-note,
.poi-coordinate {
  margin: 0;
  color: var(--text-secondary);
}
.poi-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.poi-icon-option {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
}
.poi-icon-option i {
  color: var(--accent-text);
  font-size: var(--text-lg-size);
}
</style>
