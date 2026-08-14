<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Server log"
      description="Inspect recent server output and refresh it while diagnosing a problem."
      icon="bi bi-terminal"
    />
    <section class="admin-card admin-log-card">
      <ServerLogTab ref="serverLogTab" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import ServerLogTab from '@/components/admin/ServerLogTab.vue';

defineOptions({ name: 'AdminServerLogSection' });

const props = defineProps<{
  active: boolean;
}>();

type ServerLogTabPublic = { activate: () => void; deactivate: () => void };
const serverLogTab = ref<ServerLogTabPublic | null>(null);

watch(
  () => props.active,
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
.admin-log-card {
  min-height: 20rem;
}
</style>
