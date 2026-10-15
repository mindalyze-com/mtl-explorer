<template>
  <div v-if="explanation" class="gh-authorization-notice" role="status">
    <strong>{{ explanation.heading }}</strong>
    <span>{{ explanation.detail }}</span>
    <RouterLink :to="GOOGLE_HEALTH_REAUTHORIZATION_ROUTE">
      Reauthorize Google Health <i class="bi bi-arrow-right" aria-hidden="true" />
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import type { GHStatusResponse } from '../api/googleHealthApi';
import { GOOGLE_HEALTH_REAUTHORIZATION_ROUTE } from '../utils/googleHealthNavigation';
import { googleHealthReauthorizationExplanation } from '../utils/googleHealthPresentation';

const props = defineProps<{ status?: GHStatusResponse | null }>();
const explanation = computed(() => googleHealthReauthorizationExplanation(props.status));
</script>

<style scoped>
.gh-authorization-notice {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  flex-basis: 100%;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  overflow-wrap: anywhere;
}
.gh-authorization-notice strong {
  color: var(--warning);
}
.gh-authorization-notice span {
  color: var(--text-secondary);
}
.gh-authorization-notice a {
  color: var(--accent-text);
  padding-block: 0.35rem;
  text-underline-offset: 0.2em;
}
@media (pointer: coarse) {
  .gh-authorization-notice a {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    min-height: 44px;
  }
}
</style>
