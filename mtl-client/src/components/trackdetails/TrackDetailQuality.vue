<template>
  <div class="quality-container" v-if="gpsTrack">

    <!-- Status Badges -->
    <div class="status-row">
      <div class="status-badge" :class="loadStatusClass">
        <i :class="loadStatusIcon"></i>
        <span>{{ gpsTrack.loadStatus ?? 'UNKNOWN' }}</span>
      </div>
      <div class="status-badge" :class="duplicateStatusClass">
        <i :class="duplicateStatusIcon"></i>
        <span>{{ gpsTrack.duplicateStatus ?? '—' }}</span>
      </div>
      <div class="status-badge status-badge--neutral" v-if="gpsTrack.didFilterOutlierByDistance">
        <i class="bi bi-funnel-fill"></i>
        <span>Outliers Filtered</span>
      </div>
    </div>

    <!-- Load Messages (if any) -->
    <details class="info-drawer" v-if="gpsTrack.loadMessages">
      <summary class="info-drawer__summary">
        <i class="bi bi-chat-left-text"></i> Load Messages
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="load-messages">
        <div v-for="(line, idx) in gpsTrack.loadMessages.split('\n')" :key="idx" class="load-messages__line">{{ line }}</div>
      </div>
    </details>

    <!-- Point Quality Metrics -->
    <div class="section-label"><i class="bi bi-dot"></i> Point Quality</div>
    <div class="metrics-grid">
      <div class="metric-tile">
        <div class="metric-tile__value metric-tile__value--sm">{{ gpsTrack.numberOfTrackPoints ?? '—' }}</div>
        <div class="metric-tile__label">Total Points</div>
      </div>
      <div class="metric-tile">
        <div class="metric-tile__value metric-tile__value--sm"
             v-tooltip.top="{ value: formatDistanceTooltip(gpsTrack.avgDistanceBetweenPoints), showDelay: 400 }">{{ formatDistance(gpsTrack.avgDistanceBetweenPoints) }}</div>
        <div class="metric-tile__label">Avg Pt. Distance</div>
      </div>
      <div class="metric-tile">
        <div class="metric-tile__value metric-tile__value--sm"
             v-tooltip.top="{ value: formatDistanceTooltip(gpsTrack.medianDistanceBetweenPoints), showDelay: 400 }">{{ formatDistance(gpsTrack.medianDistanceBetweenPoints) }}</div>
        <div class="metric-tile__label">Median Pt. Distance</div>
      </div>
      <div class="metric-tile">
        <div class="metric-tile__value metric-tile__value--sm"
             v-tooltip.top="{ value: formatDistanceTooltip(gpsTrack.maxDistanceBetweenPoints), showDelay: 400 }">{{ formatDistance(gpsTrack.maxDistanceBetweenPoints) }}</div>
        <div class="metric-tile__label">Max Pt. Distance</div>
      </div>
    </div>

    <!-- Duplicate Info (if duplicate) -->
    <div class="duplicate-info" v-if="gpsTrack.duplicateOf">
      <i class="bi bi-files info-drawer__summary-icon"></i>
      <span>Duplicate of track </span>
      <a class="track-link" @click="$emit('navigate-track', gpsTrack.duplicateOf)">#{{ gpsTrack.duplicateOf }}</a>
      <span v-if="gpsTrack.duplicateDetails" class="duplicate-info__detail">— {{ gpsTrack.duplicateDetails }}</span>
    </div>

    <!-- Activity Classification -->
    <div class="section-label"><i class="bi bi-tag"></i> Activity Classification</div>
    <div class="info-list info-list--inline">
      <div class="info-row">
        <span class="info-key">Activity</span>
        <span class="info-val"><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></span>
      </div>
      <div class="info-row">
        <span class="info-key">Source</span>
        <span class="info-val">
          <span class="source-badge" :class="activitySourceClass">{{ gpsTrack.activityTypeSource ?? '—' }}</span>
        </span>
      </div>
      <div class="info-row" v-if="gpsTrack.activityTypeSourceDetails">
        <span class="info-key">Source Details</span>
        <span class="info-val info-val--muted">{{ gpsTrack.activityTypeSourceDetails }}</span>
      </div>
    </div>

    <!-- Geo Coverage -->
    <div class="section-label" v-if="hasGeo"><i class="bi bi-bounding-box"></i> Geo Coverage</div>
    <div class="info-list info-list--inline" v-if="hasGeo">
      <div class="info-row" v-if="gpsTrack.centerLat != null">
        <span class="info-key">Center</span>
        <span class="info-val info-val--mono">{{ formatCoord(gpsTrack.centerLat) }}, {{ formatCoord(gpsTrack.centerLng) }}</span>
      </div>
      <div class="info-row" v-if="gpsTrack.bboxMinLat != null">
        <span class="info-key">Bbox Lat</span>
        <span class="info-val info-val--mono">{{ formatCoord(gpsTrack.bboxMinLat) }} → {{ formatCoord(gpsTrack.bboxMaxLat) }}</span>
      </div>
      <div class="info-row" v-if="gpsTrack.bboxMinLng != null">
        <span class="info-key">Bbox Lng</span>
        <span class="info-val info-val--mono">{{ formatCoord(gpsTrack.bboxMinLng) }} → {{ formatCoord(gpsTrack.bboxMaxLng) }}</span>
      </div>
      <div class="info-row" v-if="gpsTrack.utmZone">
        <span class="info-key">UTM Zone</span>
        <span class="info-val">{{ gpsTrack.utmZone }}</span>
      </div>
    </div>

    <!-- GPX Metadata (collapsible) -->
    <details class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-file-earmark-code"></i> GPX Metadata
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="info-list">
        <div class="info-row"><span class="info-key">GPX Version</span><span class="info-val">{{ gpsTrack.gpxVersion ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Track Type</span><span class="info-val">{{ gpsTrack.trackType ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Meta Name</span><span class="info-val">{{ gpsTrack.metaName ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Meta Description</span><span class="info-val">{{ gpsTrack.metaDescription ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Meta Author</span><span class="info-val">{{ gpsTrack.metaAuthor ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Meta Time</span><span class="info-val">{{ gpsTrack.metaTime ? formatDateAndTime(new Date(gpsTrack.metaTime)) : '—' }}</span></div>
        <div class="info-row"><span class="info-key">Meta Link</span><span class="info-val">{{ gpsTrack.metaLink ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">Garmin Activity ID</span><span class="info-val">{{ gpsTrack.garminActivityId ?? '—' }}</span></div>
      </div>
    </details>

    <!-- File & Indexer (collapsible) -->
    <details class="info-drawer" v-if="gpsTrack.indexedFile">
      <summary class="info-drawer__summary">
        <i class="bi bi-hdd"></i> File &amp; Indexer
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="info-list">
        <div class="info-row"><span class="info-key">File ID</span><span class="info-val">{{ gpsTrack.indexedFile.id ?? '—' }}</span></div>
        <div class="info-row"><span class="info-key">File Name</span><span class="info-val">{{ gpsTrack.indexedFile.name }}</span></div>
        <div class="info-row"><span class="info-key">Path</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.path }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.basePath"><span class="info-key">Base Path</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.basePath }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.fullPath"><span class="info-key">Full Path</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.fullPath }}</span></div>
        <div class="info-row"><span class="info-key">Size</span><span class="info-val">{{ formatBytes(gpsTrack.indexedFile.size) }}</span></div>
        <div class="info-row"><span class="info-key">Hash</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.hash ?? '—' }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.lastModifiedDate"><span class="info-key">Last Modified</span><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.lastModifiedDate)) }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.indexerStatus"><span class="info-key">Indexer Status</span><span class="info-val">{{ gpsTrack.indexedFile.indexerStatus }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.indexerInvocations != null"><span class="info-key">Indexer Runs</span><span class="info-val">{{ gpsTrack.indexedFile.indexerInvocations }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.indexerId"><span class="info-key">Indexer ID</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.indexerId }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.lastMessage"><span class="info-key">Last Message</span><span class="info-val">{{ gpsTrack.indexedFile.lastMessage }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.indexAddedDate"><span class="info-key">Index Added</span><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.indexAddedDate)) }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.indexUpdateDate"><span class="info-key">Index Updated</span><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.indexUpdateDate)) }}</span></div>
        <div class="info-row" v-if="gpsTrack.indexedFile.index"><span class="info-key">Index</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.index }}</span></div>
        <div class="info-row"><span class="info-key">Track Created</span><span class="info-val">{{ gpsTrack.createDate ? formatDateAndTime(new Date(gpsTrack.createDate)) : '—' }}</span></div>
        <div class="info-row"><span class="info-key">Track Updated</span><span class="info-val">{{ gpsTrack.updateDate ? formatDateAndTime(new Date(gpsTrack.updateDate)) : '—' }}</span></div>
      </div>
    </details>

  </div>
</template>

<script lang="ts">
import {defineComponent} from "vue";
import {formatBytes, formatDateAndTime, formatDistance, formatNumber, formatDistanceTooltip} from "@/utils/Utils";
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

export default defineComponent({
  name: 'TrackDetailQuality',
  components: { ActivityTypeBadge },
  props: {
    gpsTrack: {type: Object, default: null},
  },
  emits: ['navigate-track'],
  computed: {
    loadStatusClass(): string {
      switch (this.gpsTrack?.loadStatus) {
        case 'SUCCESS':    return 'status-badge--success';
        case 'FAILED':     return 'status-badge--error';
        case 'EMPTY_FILE': return 'status-badge--warning';
        default:           return 'status-badge--neutral';
      }
    },
    loadStatusIcon(): string {
      switch (this.gpsTrack?.loadStatus) {
        case 'SUCCESS':    return 'bi bi-check-circle-fill';
        case 'FAILED':     return 'bi bi-x-circle-fill';
        case 'EMPTY_FILE': return 'bi bi-exclamation-circle-fill';
        default:           return 'bi bi-question-circle';
      }
    },
    duplicateStatusClass(): string {
      switch (this.gpsTrack?.duplicateStatus) {
        case 'UNIQUE':           return 'status-badge--success';
        case 'DUPLICATE':        return 'status-badge--warning';
        case 'NOT_CHECKED_YET':  return 'status-badge--neutral';
        case 'EXCLUDED':         return 'status-badge--neutral';
        default:                 return 'status-badge--neutral';
      }
    },
    duplicateStatusIcon(): string {
      switch (this.gpsTrack?.duplicateStatus) {
        case 'UNIQUE':          return 'bi bi-check2';
        case 'DUPLICATE':       return 'bi bi-files';
        case 'NOT_CHECKED_YET': return 'bi bi-hourglass-split';
        case 'EXCLUDED':        return 'bi bi-slash-circle';
        default:                return 'bi bi-question-circle';
      }
    },
    activitySourceClass(): string {
      switch (this.gpsTrack?.activityTypeSource) {
        case 'USER_SET':   return 'source-badge--user';
        case 'AUTO_GUESS': return 'source-badge--auto';
        case 'FAILED':     return 'source-badge--failed';
        default:           return '';
      }
    },
    hasGeo(): boolean {
      return this.gpsTrack?.centerLat != null || this.gpsTrack?.bboxMinLat != null || this.gpsTrack?.utmZone != null;
    },
  },
  methods: {
    formatDistance,
    formatBytes,
    formatDateAndTime,
    formatNumber,
    formatDistanceTooltip,
    formatCoord(v: number | null | undefined): string {
      return v != null ? v.toFixed(6) : '—';
    },
  },
});
</script>

<style scoped>
/* ── Container ── */
.quality-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding-bottom: 0.5rem;
}

/* ── Status Row ── */
.status-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--border-subtle);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  border: 1px solid transparent;
}

.status-badge i { font-size: 0.75rem; }

.status-badge--success {
  background: var(--success-bg);
  color: var(--success);
  border-color: rgba(22, 163, 74, 0.2);
}
.status-badge--error {
  background: var(--error-bg);
  color: var(--error);
  border-color: rgba(220, 38, 38, 0.2);
}
.status-badge--warning {
  background: var(--warning-bg);
  color: var(--warning);
  border-color: rgba(217, 119, 6, 0.2);
}
.status-badge--neutral {
  background: var(--surface-elevated);
  color: var(--text-muted);
  border-color: var(--border-default);
}

/* ── Section Label ── */
.section-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 0.75rem 1rem 0.35rem;
}
.section-label i { font-size: 0.75rem; opacity: 0.7; }

/* ── Metrics Grid ── */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 0 0.5rem;
  margin-bottom: 0.25rem;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 0.6rem 0.4rem;
  border-radius: 8px;
  background: var(--surface-elevated);
  margin: 0.2rem;
}

.metric-tile__value--sm {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.metric-tile__label {
  font-size: 0.68rem;
  color: var(--text-muted);
  margin-top: 0.2rem;
  letter-spacing: 0.02em;
}

.metric-tile--energy {
  border: 1px solid var(--accent-subtle);
  background: var(--accent-bg);
}

/* ── Duplicate Info ── */
.duplicate-info {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin: 0.25rem 1rem 0.5rem;
  padding: 0.55rem 0.85rem;
  border-radius: 8px;
  background: var(--warning-bg);
  border: 1px solid rgba(217, 119, 6, 0.2);
  font-size: 0.82rem;
  color: var(--warning-text);
}

.duplicate-info__detail {
  opacity: 0.75;
  font-style: italic;
}

/* ── Source Badge ── */
.source-badge {
  display: inline-flex;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}
.source-badge--user   { background: var(--success-bg);  color: var(--success); }
.source-badge--auto   { background: var(--accent-bg);   color: var(--accent-text); }
.source-badge--failed { background: var(--error-bg);    color: var(--error); }

/* ── Info List ── */
.info-list {
  display: flex;
  flex-direction: column;
  padding: 0.25rem 0;
}

.info-list--inline {
  padding: 0 0.5rem 0.25rem;
}

.info-list--inline .info-row {
  border-radius: 4px;
  padding: 0.3rem 0.5rem;
  border-top: 1px solid var(--border-subtle);
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  padding: 0.35rem 0.9rem;
  font-size: 0.8rem;
  border-top: 1px solid var(--border-subtle);
}

.info-key {
  flex: 0 0 7rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.info-val {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.info-val--mono {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: 0.72rem;
  opacity: 0.8;
}

.info-val--muted {
  color: var(--text-muted);
  font-style: italic;
  font-size: 0.77rem;
}

/* ── Info Drawer ── */
.info-drawer {
  margin: 0.35rem 0.5rem 0;
  border-radius: 8px;
  border: 1px solid var(--border-default);
  overflow: hidden;
}

.info-drawer[open] .info-drawer__chevron {
  transform: rotate(180deg);
}

.info-drawer__chevron {
  transition: transform 0.2s ease;
}

.info-drawer__summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  list-style: none;
  background: var(--surface-elevated);
}

.info-drawer__summary::-webkit-details-marker { display: none; }
.info-drawer__summary i:first-child { font-size: 0.8rem; }
.info-drawer__summary .info-drawer__chevron { margin-left: auto; font-size: 0.75rem; }

/* ── Load Messages ── */
.load-messages {
  padding: 0.5rem 0.9rem;
  font-size: 0.78rem;
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  line-height: 1.6;
}

.load-messages__line:empty::after {
  content: '\00a0';
}

/* ── Track Link ── */
.track-link {
  color: var(--accent-text);
  cursor: pointer;
  text-decoration: none;
  font-weight: 600;
}
.track-link:hover { text-decoration: underline; }

/* ── Mobile ── */
@media (max-width: 480px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-key {
    flex: 0 0 5.5rem;
  }
}
</style>

