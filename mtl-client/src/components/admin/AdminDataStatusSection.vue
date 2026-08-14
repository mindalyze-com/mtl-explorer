<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Data status"
      description="Compare the data loaded by this browser with the current server revisions."
      icon="bi bi-database-check"
    />
    <DataFreshnessTab @refresh-data="$emit('refresh-data', $event)" />
  </div>
</template>

<script setup lang="ts">
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import DataFreshnessTab from '@/components/admin/DataFreshnessTab.vue';

defineOptions({ name: 'AdminDataStatusSection' });

defineEmits<{
  (event: 'refresh-data', done: (success?: boolean) => void): void;
}>();
</script>
