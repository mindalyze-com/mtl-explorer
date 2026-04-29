<template>
  <div class="tab-content log-tab">

    <!-- Disabled / demo mode -->
    <div v-if="disabled" class="log-notice log-notice--warn">
      <i class="pi pi-lock"/>
      <span>Server log viewer is disabled for this instance.</span>
    </div>

    <!-- Error loading -->
    <div v-else-if="error" class="log-notice log-notice--error">
      <i class="pi pi-times-circle"/>
      <span>{{ error }}</span>
    </div>

    <template v-else>
      <!-- Toolbar -->
      <div class="log-toolbar">
        <Select
          v-model="requestedLines"
          :options="lineOptions"
          option-label="label"
          option-value="value"
          class="log-lines-select"
          @change="fetchLog"
        />
        <span class="log-meta" v-if="lastUpdated">
          <i class="pi pi-history" style="font-size: var(--text-xs-size);"/>
          {{ timeSinceUpdate }}
        </span>
        <div class="log-toolbar-actions">
          <Button
            :icon="wrapLines ? 'pi pi-align-left' : 'pi pi-align-justify'"
            :label="wrapLines ? 'Wrap' : 'No wrap'"
            size="small"
            severity="secondary"
            :outlined="!wrapLines"
            @click="wrapLines = !wrapLines"
          />
          <Button
            label="Refresh"
            icon="pi pi-refresh"
            size="small"
            :loading="loading"
            :disabled="loading"
            @click="fetchLog"
          />
        </div>
      </div>

      <!-- Log output -->
      <div class="log-output-wrapper">
        <div v-if="!logLines.length && loading" class="log-placeholder">
          <i class="pi pi-spin pi-spinner"/> Loading log…
        </div>
        <div v-else-if="!logLines.length" class="log-placeholder">
          No log output yet.
        </div>
        <div v-else class="log-pre-wrapper">
          <pre class="log-pre" :class="{ 'log-pre--wrap': wrapLines }">{{ displayContent }}</pre>
        </div>
      </div>

    </template>

  </div>
</template>

<script>
import { defineComponent } from 'vue';
import { getServerLog } from '@/utils/ServiceHelper';

const LINE_OPTIONS = [
  { label: '50 lines',    value: 50   },
  { label: '100 lines',   value: 100  },
  { label: '200 lines',   value: 200  },
  { label: '500 lines',   value: 500  },
  { label: '1 000 lines', value: 1000 },
  { label: '2 000 lines', value: 2000 },
];

export default defineComponent({
  name: 'ServerLogTab',
  data() {
    return {
      logLines: [],
      loading: false,
      error: '',
      disabled: false,
      requestedLines: 200,
      lastUpdated: null,
      lineOptions: LINE_OPTIONS,
      wrapLines: false,
    };
  },
  computed: {
    displayContent() {
      return [...this.logLines].reverse().join('\n');
    },
    timeSinceUpdate() {
      if (!this.lastUpdated) return '';
      const secs = Math.floor((Date.now() - this.lastUpdated) / 1000);
      if (secs < 5) return 'just now';
      if (secs < 60) return `${secs}s ago`;
      return `${Math.floor(secs / 60)}m ${secs % 60}s ago`;
    },
  },
  methods: {
    async fetchLog() {
      this.loading = true;
      this.error = '';
      try {
        const text = await getServerLog(this.requestedLines);
        if (text === '' && !this.logLines.length) {
          this.disabled = true;
        } else {
          this.disabled = false;
          this.logLines = text ? text.trim().split('\n') : [];
          this.lastUpdated = Date.now();
        }
      } catch (e) {
        this.error = 'Failed to fetch log: ' + (e?.message || e);
      } finally {
        this.loading = false;
      }
    },
    activate() {
      this.logLines = [];
      this.error = '';
      this.disabled = false;
      this.fetchLog();
    },
    deactivate() {
      // no polling to tear down
    },
  },
});
</script>

<style scoped>
.log-tab {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  height: 100%;
}

.log-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.7rem 0.9rem;
  border-radius: 6px;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  margin-top: 0.4rem;
}

.log-notice--warn {
  background: var(--warning-bg);
  border: 1px solid color-mix(in srgb, var(--warning) 30%, transparent);
  color: var(--warning-text);
}

.log-notice--error {
  background: var(--error-bg);
  border: 1px solid color-mix(in srgb, var(--error) 30%, transparent);
  color: var(--error);
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.25rem 0;
}

.log-lines-select {
  width: 7.5rem;
  font-size: var(--text-sm-size);
}

.log-meta {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.log-toolbar-actions {
  margin-left: auto;
}

.log-output-wrapper {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  background: var(--surface-glass-heavy);
}

.log-placeholder {
  padding: 1.5rem;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.log-pre-wrapper {
  height: 100%;
  overflow: auto;
}

.log-pre {
  margin: 0;
  padding: 0.6rem 0.75rem;
  font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', monospace;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  white-space: pre;
  box-sizing: border-box;
}

.log-pre--wrap {
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
