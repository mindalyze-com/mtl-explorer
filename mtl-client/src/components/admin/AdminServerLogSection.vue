<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Logs"
      description="Inspect stored diagnostics or recent server output."
      icon="bi bi-terminal"
    />
    <section class="admin-card admin-log-card">
      <div class="log-tabs" role="group" aria-label="Log view">
        <Button
          label="Stored events"
          size="small"
          :severity="view === 'stored' ? 'primary' : 'secondary'"
          :aria-pressed="view === 'stored'"
          @click="view = 'stored'"
        />
        <Button
          label="Server file"
          size="small"
          :severity="view === 'file' ? 'primary' : 'secondary'"
          :aria-pressed="view === 'file'"
          @click="view = 'file'"
        />
      </div>
      <StoredSystemLogs v-if="view === 'stored'" :active="active" />
      <ServerLogTab v-else ref="serverLogTab" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import Button from 'primevue/button';
import StoredSystemLogs from '@/components/admin/StoredSystemLogs.vue';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import ServerLogTab from '@/components/admin/ServerLogTab.vue';

defineOptions({ name: 'AdminServerLogSection' });

const props = defineProps<{
  active: boolean;
}>();

type ServerLogTabPublic = { activate: () => void; deactivate: () => void };
const view = ref<'stored' | 'file'>('stored');
const serverLogTab = ref<ServerLogTabPublic | null>(null);

watch(
  () => props.active && view.value === 'file',
  (active) => {
    void nextTick(() => {
      if (active) serverLogTab.value?.activate();
      else serverLogTab.value?.deactivate();
    });
  },
  { immediate: true }
);

onBeforeUnmount(() => serverLogTab.value?.deactivate());
</script>

<style scoped>
.log-tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.admin-log-card {
  min-height: 20rem;
}
</style>
