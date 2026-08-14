<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Processing"
      description="Monitor file indexers, track processing, maps, and routing services."
      icon="bi bi-list-check"
    />
    <IndexerStatusTab />
  </div>
</template>

<script setup lang="ts">
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';

defineOptions({ name: 'AdminProcessingSection' });
</script>
