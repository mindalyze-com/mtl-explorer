<template>
  <details class="gh-stream" :class="{ 'gh-stream--attention': needsAttention }">
    <summary>
      <span class="gh-stream__name"
        ><strong>{{ formatDataType(stream.dataType) }}</strong
        ><span>{{ formatRepresentation(stream.representation) }}</span></span
      >
      <span class="gh-stream__state">{{ statusLabel }}</span>
      <i class="bi bi-chevron-down gh-stream__chevron" aria-hidden="true" />
    </summary>
    <div class="gh-stream__body">
      <section aria-label="Recent checks">
        <h5>Recent checks</h5>
        <dl>
          <template v-if="stream.lastFastCompletedAt"
            ><dt>Last complete fast check</dt>
            <dd>{{ formatDateTime(stream.lastFastCompletedAt) }}</dd></template
          >
          <template v-if="stream.fastStart && stream.fastEnd"
            ><dt>Fast window</dt>
            <dd>{{ nativeRange(stream.fastStart, stream.fastEnd) }}</dd></template
          >
          <template v-if="stream.newestStoredRecord"
            ><dt>Newest stored record</dt>
            <dd>{{ formatNativeTime(stream.newestStoredRecord, stream.timeDomain) }}</dd></template
          >
          <template v-if="stream.fullPass"
            ><dt>Full recent pass</dt>
            <dd>{{ formatWorkState(stream.fullPass.state) }} · {{ stream.fullPass.cycles ?? 0 }} cycles</dd></template
          >
          <template v-if="stream.fullPass?.start && stream.fullPass?.end"
            ><dt>Full target</dt>
            <dd>{{ nativeRange(stream.fullPass.start, stream.fullPass.end) }}</dd></template
          >
          <template v-if="stream.fullPass?.state === 'ACTIVE' && stream.fullPass.nextEnd"
            ><dt>Continuing before</dt>
            <dd>{{ formatNativeTime(stream.fullPass.nextEnd, stream.timeDomain) }}</dd></template
          >
          <template v-if="stream.fullPass?.nextDueAt"
            ><dt>Next full refresh</dt>
            <dd>{{ formatDateTime(stream.fullPass.nextDueAt) }}</dd></template
          >
          <template v-if="stream.fullPass?.skipped"
            ><dt>Skipped chunks</dt>
            <dd>{{ formatCount(stream.fullPass.skipped) }}</dd></template
          >
          <template v-if="stream.fullPass?.handedOver"
            ><dt>Handed to history</dt>
            <dd>{{ formatCount(stream.fullPass.handedOver) }}</dd></template
          >
        </dl>
        <p v-if="stream.budgetLimited" class="gh-stream__warning">
          Fast coverage is incomplete; cycle capacity was limited.
        </p>
      </section>
      <section aria-label="Historical checks">
        <h5>Historical checks</h5>
        <dl>
          <template v-if="stream.finalizedBackTo"
            ><dt>Finalized back to</dt>
            <dd>{{ formatNativeTime(stream.finalizedBackTo, 'DATE') }}</dd></template
          >
          <dt>Pending day checks</dt>
          <dd>{{ formatCount(stream.pendingDays ?? 0) }}</dd>
          <dt>Parked day checks</dt>
          <dd>{{ formatCount(stream.parkedDays ?? 0) }}</dd>
          <template v-if="stream.activeHistoryStart && stream.activeHistoryEnd"
            ><dt>Current history range</dt>
            <dd>{{ nativeRange(stream.activeHistoryStart, stream.activeHistoryEnd) }}</dd></template
          >
        </dl>
        <p v-if="stream.pendingDays === 0">No unchecked historical days remain.</p>
      </section>
      <ul
        v-if="stream.failures?.length"
        class="gh-stream__failures"
        :aria-label="`${formatDataType(stream.dataType)} ranges awaiting retry`"
      >
        <li v-for="(failure, index) in stream.failures" :key="index">
          <strong>{{ formatWorkState(failure.state) }} · {{ nativeRange(failure.start, failure.end) }}</strong>
          <span v-if="failure.attempts">Attempts: {{ failure.attempts }}</span>
          <span v-if="failure.state === 'WAITING' && failure.nextRetryAt"
            >Retry during a cycle after {{ formatDateTime(failure.nextRetryAt) }}</span
          >
          <span v-else-if="failure.state === 'PARKED' || failure.state === 'SKIPPED'"
            >Use Retry failed ranges in Manage connection.</span
          >
          <span v-if="failure.error">{{ failure.error }}</span>
        </li>
      </ul>
    </div>
  </details>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { GHStreamSyncStatus } from 'x8ing-mtl-api-typescript-fetch';
import {
  formatDataType,
  formatRepresentation,
  formatWorkState,
  formatDateTime,
  formatNativeTime,
  formatCount,
} from '../utils/googleHealthSyncFormatting';
const props = defineProps<{ stream: GHStreamSyncStatus }>();
const needsAttention = computed(
  () => props.stream.budgetLimited || (props.stream.failures?.length ?? 0) > 0 || (props.stream.parkedDays ?? 0) > 0
);
const statusLabel = computed(() =>
  props.stream.budgetLimited
    ? 'Limited'
    : needsAttention.value
      ? 'Needs retry'
      : props.stream.fullPass?.state === 'ACTIVE'
        ? 'Refresh pending'
        : (props.stream.pendingDays ?? 0) > 0
          ? 'History pending'
          : props.stream.lastFastCompletedAt instanceof Date || props.stream.finalizedBackTo instanceof Date
            ? 'Checked'
            : 'Waiting for first check'
);
function nativeRange(start?: Date, end?: Date): string {
  return start instanceof Date && end instanceof Date
    ? `${formatNativeTime(start, props.stream.timeDomain)}–${formatNativeTime(end, props.stream.timeDomain)}`
    : '';
}
</script>
<style scoped>
.gh-stream {
  border-bottom: 1px solid var(--border-default);
  min-width: 0;
  background: transparent;
}
.gh-stream summary {
  cursor: pointer;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 44px;
  padding: 0.55rem 0;
}
.gh-stream summary::-webkit-details-marker {
  display: none;
}
.gh-stream summary:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: 6px;
}
.gh-stream__name {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}
.gh-stream__name strong {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.gh-stream__name span {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.gh-stream__state {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  flex: 0 1 auto;
  text-align: right;
}
.gh-stream--attention .gh-stream__state {
  color: var(--warning-status-text);
}
.gh-stream__chevron {
  flex: none;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.gh-stream[open] .gh-stream__chevron {
  transform: rotate(180deg);
}
.gh-stream__body {
  padding: 0.2rem 0 0.65rem;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr));
  gap: 0.7rem;
  border-top: 1px solid var(--border-subtle);
}
.gh-stream__body section {
  min-width: 0;
}
.gh-stream h5 {
  margin: 0.6rem 0 0.4rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.gh-stream dl {
  margin: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr);
  gap: 0.3rem 0.55rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.gh-stream dt {
  color: var(--text-muted);
}
.gh-stream dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}
.gh-stream p {
  margin: 0.45rem 0 0;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.gh-stream .gh-stream__warning {
  color: var(--warning-status-text);
}
.gh-stream__failures {
  grid-column: 1/-1;
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.5rem;
}
.gh-stream__failures li {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.5rem;
  border-radius: 5px;
  background: color-mix(in srgb, var(--warning) 8%, var(--surface-sheet-solid));
  color: var(--warning-status-text);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow-wrap: anywhere;
}
@media (max-width: 560px) {
  .gh-stream dl {
    grid-template-columns: 1fr;
    gap: 0.15rem;
  }
  .gh-stream dd {
    margin-bottom: 0.35rem;
  }
}
</style>
