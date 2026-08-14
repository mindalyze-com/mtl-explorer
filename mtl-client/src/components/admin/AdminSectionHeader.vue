<template>
  <header class="admin-page-header">
    <span class="admin-page-header__icon" aria-hidden="true"><i :class="icon" /></span>
    <div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
  </header>
</template>

<script setup lang="ts">
defineOptions({ name: 'AdminSectionHeader' });

defineProps<{
  title: string;
  description: string;
  icon: string;
}>();
</script>
