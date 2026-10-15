<template>
  <BottomSheet
    v-model="isOpen"
    :icon="NAVIGATION_TOOL_ICONS.health"
    :detents="[
      { id: 'comfortable', height: 'min(900px, 92vh)' },
      { id: 'large', height: '94vh' },
    ]"
    initial-detent="comfortable"
    :content-key="screen"
    :back-label="screen === 'hub' ? '' : 'Back to Health'"
    scroll-hint-label="More health data"
    sheet-class="sheet--solid-over-map"
    @back="showHub"
    @closed="onSheetClosed"
  >
    <template #title>
      <div class="health-header-nav">
        <i v-if="screen === 'hub'" :class="[NAVIGATION_TOOL_ICONS.health, 'health-sheet-icon']" aria-hidden="true"></i>
        <h2 ref="sectionTitle" class="panel-title" tabindex="-1">
          {{ currentSection ? `${currentSection.label} (beta)` : GOOGLE_HEALTH_FEATURE_LABEL }}
        </h2>
      </div>
    </template>

    <div v-if="demoMode === null" class="health-demo-loading" aria-live="polite">
      <i class="pi pi-spin pi-spinner" aria-hidden="true" />
      <span>Checking Google Health availability…</span>
    </div>
    <div v-else-if="screen === 'hub'" class="health-hub sheet-scroll-target" data-test="health-hub">
      <SectionHub
        ref="sectionHub"
        title="Explore your health data"
        :description="
          syntheticData
            ? 'Explore synthetic health trends and track examples.'
            : `Review trends and records from Google Health. ${GOOGLE_HEALTH_BETA_NOTICE}`
        "
        :items="healthSections"
        @open="openSection"
      />
    </div>
    <HealthOverviewScreen
      v-else-if="screen === 'overview'"
      :active="isOpen && screen === 'overview'"
      :demo-mode="demoMode"
      :synthetic-data="syntheticData"
      @open-data-details="openSection('data')"
    />
    <div v-else-if="demoMode === false || syntheticData" class="health-data-screen">
      <HealthRawDataScreen :active="isOpen && screen === 'data'" :synthetic-data="syntheticData" />
    </div>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { NAVIGATION_TOOL_ICONS } from '@/components/ui/navigationToolIcons';
import SectionHub, { type SectionHubItem } from '@/components/ui/SectionHub.vue';
import { getDemoStatus } from '@/utils/serverAdminApi';
import { GOOGLE_HEALTH_BETA_NOTICE, GOOGLE_HEALTH_FEATURE_LABEL } from './utils/googleHealthPresentation';
import HealthOverviewScreen from './screens/overview/HealthOverviewScreen.vue';
import HealthRawDataScreen from './screens/raw/HealthRawDataScreen.vue';

defineOptions({ name: 'HealthTool' });

const emit = defineEmits<{
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
}>();

const route = useRoute();
const router = useRouter();
const isOpen = ref(false);
type HealthSection = 'overview' | 'data';
type HealthScreen = 'hub' | HealthSection;
type HealthNavigationState = { screen: HealthScreen };

const HEALTH_SECTIONS = [
  {
    value: 'overview',
    label: 'Overview',
    description: 'Charts, latest values, and trends across your health data.',
    icon: 'bi bi-graph-up',
    actionLabel: 'Open overview',
  },
  {
    value: 'data',
    label: 'Data & Records',
    description: 'Review mirrored data types, sources, and individual records.',
    icon: 'bi bi-database',
    actionLabel: 'Browse data',
  },
] as const satisfies readonly SectionHubItem[];

const screen = ref<HealthScreen>('hub');
const demoMode = ref<boolean | null>(null);
const syntheticData = ref(false);
const sectionHub = ref<InstanceType<typeof SectionHub> | null>(null);
const sectionTitle = ref<HTMLElement | null>(null);
const healthSections = computed<readonly SectionHubItem[]>(() =>
  demoMode.value && !syntheticData.value
    ? HEALTH_SECTIONS.filter((section) => section.value === 'overview')
    : HEALTH_SECTIONS
);
const currentSection = computed(() => HEALTH_SECTIONS.find((section) => section.value === screen.value));

onMounted(async () => {
  try {
    const availability = await getDemoStatus();
    demoMode.value = availability.demoMode;
    syntheticData.value = availability.demoMode && availability.syntheticHealthData === true;
  } catch {
    // Do not leave the Health tool on an endless availability spinner.
    // The server still enforces demo-mode access when this public probe fails.
    demoMode.value = false;
  }
  if (demoMode.value && !syntheticData.value && screen.value === 'data') screen.value = 'hub';
});

function toggle() {
  if (isOpen.value) close();
  else open();
}

function open() {
  if (isOpen.value) return;
  isOpen.value = true;
  screen.value = 'hub';
  emit('tool-opened');
}

function close() {
  isOpen.value = false;
}

function openSection(value: string): void {
  const section = HEALTH_SECTIONS.find((item) => item.value === value);
  if (!section || (section.value === 'data' && demoMode.value !== false && !syntheticData.value)) return;
  screen.value = section.value;
}

async function showHub(): Promise<void> {
  const previousSection = screen.value;
  screen.value = 'hub';
  await nextTick();
  if (previousSection !== 'hub') sectionHub.value?.focusItem(previousSection);
}

function getNavigationState(): HealthNavigationState {
  return { screen: screen.value };
}

function restoreNavigationState(state: unknown): void {
  if (!state || typeof state !== 'object' || !('screen' in state)) return;
  const target = state.screen;
  if (
    target === 'hub' ||
    target === 'overview' ||
    (target === 'data' && (demoMode.value !== true || syntheticData.value))
  ) {
    screen.value = target;
  }
}

watch(screen, async (nextScreen) => {
  if (nextScreen === 'hub') return;
  await nextTick();
  sectionTitle.value?.focus({ preventScroll: true });
});

async function onSheetClosed() {
  isOpen.value = false;
  if (route.name === 'health') await router.replace({ name: 'home' }).catch(() => undefined);
  emit('tool-closed');
}

defineExpose({ open, toggle, close, getNavigationState, restoreNavigationState });
</script>

<style scoped>
.health-header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.health-sheet-icon {
  font-size: var(--text-lg-size);
  color: var(--text-secondary);
  flex-shrink: 0;
}

.health-hub {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-top: 0.5rem;
}

.health-data-screen {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  width: 100%;
}

.health-demo-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
</style>
