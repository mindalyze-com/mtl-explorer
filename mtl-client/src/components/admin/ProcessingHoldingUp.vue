<template>
  <span class="processing-blocker">
    <template v-if="jobs.length <= NAMED_JOBS">
      Holding up
      <template v-for="(job, index) in jobs" :key="job">
        <template v-if="index"> and </template>
        <button type="button" @click="emit('reveal', `JOB:${job}`)">
          {{ targetLabel({ kind: 'JOB', id: job }) }}
        </button></template
      >.
    </template>
    <template v-else>Holding up {{ jobs.length }} steps.</template>
  </span>
</template>

<script setup lang="ts">
import type { ProcessingBlocker } from '@/utils/processingJobPresentation';

defineProps<{
  /** Jobs currently waiting for this row. */
  jobs: string[];
  targetLabel: (blocker: ProcessingBlocker) => string;
}>();
const emit = defineEmits<{ reveal: [key: string] }>();
/** Up to this many waiting jobs are named; more are counted. */
const NAMED_JOBS = 2;
</script>
