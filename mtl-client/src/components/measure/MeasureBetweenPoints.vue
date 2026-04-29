<template>
  <div>
    <div v-if="active && overlayZoneNodes.length" class="measure-map-overlay">
      <div class="measure-flow-rail">
        <template v-for="(node, idx) in overlayZoneNodes" :key="node.key">
          <div class="measure-flow-node" :class="node.toneClass">
            <div class="measure-flow-node-circle">{{ node.label }}</div>
            <div class="measure-flow-node-value">{{ node.value }}</div>
            <div class="measure-flow-node-detail">{{ node.detail }}</div>
          </div>
          <div v-if="idx < overlayZoneNodes.length - 1 || sharedFlowNode" class="measure-flow-connector" :class="node.connectorClass"></div>
        </template>

        <div v-if="sharedFlowNode" class="measure-flow-node measure-flow-node--final" :class="sharedFlowNode.toneClass">
          <div class="measure-flow-node-target">
            <span>{{ sharedFlowNode.value }}</span>
          </div>
          <div class="measure-flow-node-value measure-flow-node-value--final">{{ sharedFlowNode.title }}</div>
          <div class="measure-flow-node-detail">{{ sharedFlowNode.detail }}</div>
        </div>
      </div>
    </div>

    <BottomSheet v-model="active" :detents="measureSheetDetents" title="Segment Analyzer" icon="bi bi-stopwatch" header-mode="compact" :no-backdrop="true" @closed="onMeasureClosed">
      <div class="measure-sheet">
        <section class="measure-controls-card measure-controls-card--dock">

          <!-- Row 1: Undo · Clear All · Analyze -->
          <div class="measure-toolbar">
            <button class="measure-toolbar-btn" :disabled="triggerPoints.length < 1 || isLoading" @click="onUndoLastPoint" title="Undo last point" aria-label="Undo last point">
              <i class="bi bi-arrow-left"></i>
              <span>Undo</span>
            </button>
            <button class="measure-toolbar-btn" :disabled="!triggerPoints.length || isLoading" @click="onCancelSelection" title="Clear all" aria-label="Clear all">
              <i class="bi bi-trash3"></i>
              <span>Clear all</span>
            </button>
            <button class="measure-toolbar-btn measure-toolbar-btn--analyze" :class="{ 'measure-toolbar-btn--ready': !isAnalyzeDisabled }" :disabled="isAnalyzeDisabled" @click="onFinishSelection">
              <i v-if="isLoading" class="bi bi-arrow-clockwise measure-spin"></i>
              <i v-else class="bi bi-graph-up-arrow"></i>
              <span>{{ isLoading ? 'Analyzing…' : 'Analyze' }}</span>
            </button>
          </div>

          <!-- Row 2: Bare radius slider -->
          <div class="measure-radius-bare">
            <Slider class="measure-bar-slider" v-model="radiusSelector" :min="1" :max="100"/>
            <span class="measure-radius-hint">{{ radiusDisplay }} m</span>
          </div>

          <!-- Row 3: Tap-map hint + explanation -->
          <div class="measure-placement-section">
            <div class="measure-placement-status">
              <span class="measure-placement-kicker">Tap map</span>
              <span class="measure-placement-text">{{ dockHintText }}</span>
            </div>
            <p class="measure-explanation">
              Place zones on the map where your routes pass through. The analyzer finds all tracks crossing every zone and compares sector times between them.
            </p>
          </div>

        </section>
      </div>
    </BottomSheet>

    <BottomSheet v-if="showResults" v-model="showResults" title="Segment Analyzer" icon="bi bi-stopwatch" header-mode="compact" :detents="[{ height: '88vh' }, { height: '98vh' }]"
                 @closed="onResultsClosed">
      <DisplayMeasureResults :measureServiceResult="measureServiceResult" @show-track-details="$emit('show-track-details', $event)"/>
    </BottomSheet>
  </div>

</template>

<script>
import {defineComponent, inject, provide, ref} from "vue";
import {fetchTrackDetailsForCrossingPoints, fetchTrackIdsWithinDistanceOfPoint} from '@/utils/ServiceHelper';
import DisplayMeasureResults from "@/components/measure/DisplayMeasureResults.vue";
import BottomSheet from "@/components/ui/BottomSheet.vue";
import {EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT} from "@/utils/Utils";


export default defineComponent({
  name: 'MeasureBetweenPoints',
  components: {DisplayMeasureResults, BottomSheet},
  emits: ['active-changed', 'tool-opened', 'tool-closed', 'show-track-details'],
  props: ['map'],
  data() {
    return {
      active: false,
      radiusSelector: 40,
      radius() {
        let val = Math.round(Math.pow(1.1, this.radiusSelector));
        if (val > 10000) {
          return Math.round(val / 1000) * 1000;
        } else if (val > 1000) {
          return Math.round(val / 100) * 100;
        } else if (val > 100) {
          return Math.round(val / 10) * 10;
        } else {
          return val;
        }
      },
      triggerPoints: [],
      zoneTrackCounts: [],
      zoneTrackIds: [],
      zoneHasVisibleTracks: [],
      zoneCountAbortControllers: [],
      radiusCountDebounceTimer: null,
      measureServiceResult: null,
      showResults: false,
      numberOfCrossings: 0,
      measureSourceIds: [],
      measureLayerIds: [],
      clickHandler: null,
      isLoading: false,
      showInfo: false,
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      viewportHeight: typeof window !== 'undefined' ? window.innerHeight : 900,
    }
  },
  watch: {
    radiusSelector() {
      this.updateAllCircles();
      this.debouncedRefreshAllZoneCounts();
    },
  },
  computed: {
    radiusDisplay() {
      return this.radius();
    },
    isAnalyzeDisabled() {
      return this.isLoading || this.triggerPoints.length === 0;
    },
    candidateTrackIdsIntersection() {
      if (!this.triggerPoints.length) {
        return [];
      }

      const loadedTrackLists = this.zoneTrackIds.slice(0, this.triggerPoints.length);
      if (loadedTrackLists.some(trackIds => !Array.isArray(trackIds))) {
        return null;
      }

      if (!loadedTrackLists.length) {
        return [];
      }

      let intersection = new Set(loadedTrackLists[0]);
      for (let i = 1; i < loadedTrackLists.length; i++) {
        const zoneIds = new Set(loadedTrackLists[i]);
        intersection = new Set([...intersection].filter(id => zoneIds.has(id)));
      }
      return [...intersection];
    },
    overlayZoneNodes() {
      return this.triggerPoints.map((point, idx) => {
        const count = this.zoneTrackCounts[idx];
        let value = '...';
        let detail = 'tracks';
        let tone = this.getZoneVisualState(idx);

        if (count === -1) {
          value = '?';
          detail = 'check';
        } else if (count === undefined) {
          value = '...';
          detail = 'tracks';
        } else {
          value = String(count);
          detail = count === 1 ? 'track' : 'tracks';
        }

        return {
          key: point.name,
          label: point.name,
          value,
          detail,
          toneClass: `measure-flow-node--${tone}`,
          connectorClass: `measure-flow-connector--${tone}`,
        };
      });
    },
    sharedFlowNode() {
      if (!this.triggerPoints.length) {
        return null;
      }

      const relevantCounts = this.zoneTrackCounts.slice(0, this.triggerPoints.length);
      if (this.triggerPoints.length === 1) {
        const count = relevantCounts[0];
        if (count === undefined) {
          return { value: '...', title: 'shared', detail: 'waiting', toneClass: 'measure-flow-node--loading' };
        }
        if (count === -1) {
          return { value: '?', title: 'shared', detail: 'check', toneClass: 'measure-flow-node--error' };
        }
        return {
          value: String(count),
          title: 'shared',
          detail: count === 1 ? 'track' : 'tracks',
          toneClass: count === 0 ? 'measure-flow-node--warning' : 'measure-flow-node--ok',
        };
      }

      if (relevantCounts.some(count => count === -1)) {
        return { value: '?', title: 'shared', detail: 'check', toneClass: 'measure-flow-node--error' };
      }
      if (relevantCounts.some(count => count === undefined)) {
        return { value: '...', title: 'shared', detail: 'tracks', toneClass: 'measure-flow-node--loading' };
      }

      const candidateCount = Array.isArray(this.candidateTrackIdsIntersection) ? this.candidateTrackIdsIntersection.length : 0;
      if (candidateCount === 0) {
        return { value: '!', title: 'shared', detail: 'tracks', toneClass: 'measure-flow-node--warning' };
      }

      return {
        value: String(candidateCount),
        title: 'shared',
        detail: candidateCount === 1 ? 'track' : 'tracks',
        toneClass: 'measure-flow-node--ok',
      };
    },
    measureSheetDetents() {
      const isMobile = this.viewportWidth <= 768;
      const compactPx = isMobile ? 350 : 280;
      const expandedVh = isMobile ? 88 : 82;
      return [
        { id: 'compact', height: `${compactPx}px` },
        { id: 'expanded', height: `${expandedVh}vh` },
      ];
    },
    dockHintText() {
      if (!this.triggerPoints.length) {
        return 'Place zone A';
      }
      const nextLabel = String.fromCharCode(65 + this.triggerPoints.length);
      return `${this.triggerPoints.length} zone${this.triggerPoints.length === 1 ? '' : 's'} ready • next: ${nextLabel}`;
    },
    selectionHint() {
      if (!this.triggerPoints.length) {
        return "Tap map to add zones";
      }
      return this.triggerPoints.length === 1
          ? "1 zone ready"
          : this.triggerPoints.length + " zones ready";
    },
  },
  setup() {
    const fakeEvent = ref('empty');
    provide(EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT, fakeEvent);

    const toast = inject("toast");

    function onMaximizeSender() {
      fakeEvent.value = "maximized" + new Date();
    }

    return {
      fakeEvent,
      toast,
      onMaximizeSender,
    };
  },
  beforeUnmount() {
    this.cleanupMapLayers();
    if (this.clickHandler && this.map) {
      this.map.off('click', this.clickHandler);
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.onViewportResize);
    }
  },
  mounted() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this.onViewportResize);
    }
  },
  methods: {

    onViewportResize() {
      this.viewportWidth = window.innerWidth;
      this.viewportHeight = window.innerHeight;
    },

    /**
     * Picks a detection radius proportional to the visible map extent.
     * Takes ~4% of the shorter (width/height) dimension, clamped to [10m, 2000m].
     *
     * Examples:
     *   extent 300m  → 12m  → 10m (floor)
     *   extent 500m  → 20m
     *   extent 1km   → 40m
     *   extent 2km   → 80m
     *   extent 5km   → 200m
     *   extent 50km  → 2000m (ceiling)
     */
    radiusSelectorForExtent() {
      const bounds = this.map.getBounds();
      const centerLat = (bounds.getNorth() + bounds.getSouth()) / 2;
      const metersPerDegLat = 111320;
      const metersPerDegLng = 111320 * Math.cos(centerLat * Math.PI / 180);
      const widthM  = Math.abs(bounds.getEast()  - bounds.getWest())  * metersPerDegLng;
      const heightM = Math.abs(bounds.getNorth() - bounds.getSouth()) * metersPerDegLat;
      const extentM = Math.min(widthM, heightM);
      const targetM = Math.max(10, Math.min(2000, extentM * 0.04));
      return Math.round(Math.max(1, Math.min(100, Math.log(targetM) / Math.log(1.1))));
    },

    async toggle() {
      this.active = !this.active;
      this.$emit('active-changed', this.active);
      if (this.active) this.$emit('tool-opened');

      if (this.active) {
        // Set radius based on current visible map extent
        if (this.map) {
          this.radiusSelector = this.radiusSelectorForExtent();
        }
        this.clickHandler = (e) => this.onMapClick(e);
        this.map.on('click', this.clickHandler);
      } else {
        if (this.clickHandler) {
          this.map.off('click', this.clickHandler);
          this.clickHandler = null;
        }
        this.cleanupMapLayers();
        this.triggerPoints = [];
      }
    },

    close() {
      if (!this.active) return;
      this.active = false;
      this.$emit('active-changed', false);
      if (this.clickHandler && this.map) {
        this.map.off('click', this.clickHandler);
        this.clickHandler = null;
      }
      this.cleanupMapLayers();
      this.triggerPoints = [];
    },

    async onCancelSelection() {
      this.cancelAllZoneCountRequests();
      this.cleanupMapLayers();
      this.triggerPoints = [];
      this.zoneTrackCounts = [];
      this.zoneTrackIds = [];
      this.zoneHasVisibleTracks = [];
    },

    onUndoLastPoint() {
      if (!this.triggerPoints.length) return;
      const idx = this.triggerPoints.length - 1;
      this.triggerPoints.pop();

      // Cancel pending count request for this zone
      if (this.zoneCountAbortControllers[idx]) {
        this.zoneCountAbortControllers[idx].abort();
        this.zoneCountAbortControllers.splice(idx, 1);
      }
      this.zoneTrackCounts.splice(idx, 1);
      this.zoneTrackIds.splice(idx, 1);
      this.zoneHasVisibleTracks.splice(idx, 1);

      // Remove map layers for this zone (3 layers + 2 sources per zone)
      const layersToRemove = [
        `measure-circle-layer-${idx}`,
        `measure-circle-outline-layer-${idx}`,
        `measure-label-layer-${idx}`,
      ];
      const sourcesToRemove = [
        `measure-circle-${idx}`,
        `measure-label-${idx}`,
      ];
      if (this.map) {
        for (const layerId of layersToRemove) {
          if (this.map.getLayer(layerId)) this.map.removeLayer(layerId);
        }
        for (const sourceId of sourcesToRemove) {
          if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);
        }
      }
      this.measureLayerIds = this.measureLayerIds.filter(id => !layersToRemove.includes(id));
      this.measureSourceIds = this.measureSourceIds.filter(id => !sourcesToRemove.includes(id));

    },

    cleanupMapLayers() {
      if (!this.map) return;
      for (const layerId of this.measureLayerIds) {
        if (this.map.getLayer(layerId)) this.map.removeLayer(layerId);
      }
      for (const sourceId of this.measureSourceIds) {
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);
      }
      this.measureLayerIds = [];
      this.measureSourceIds = [];
      this.cancelAllZoneCountRequests();
    },

    cancelAllZoneCountRequests() {
      for (const ac of this.zoneCountAbortControllers) {
        if (ac) ac.abort();
      }
      this.zoneCountAbortControllers = [];
      if (this.radiusCountDebounceTimer) {
        clearTimeout(this.radiusCountDebounceTimer);
        this.radiusCountDebounceTimer = null;
      }
    },

    async onFinishSelection() {
      if (this.isAnalyzeDisabled) {
        return;
      }
      this.isLoading = true;
      try {
        const data = await fetchTrackDetailsForCrossingPoints(this.triggerPoints, this.radius());
        this.measureServiceResult = data;
        this.numberOfCrossings = (this.measureServiceResult.crossings != null ? Object.keys(this.measureServiceResult.crossings).length : 0);
        this.active = false;
        this.$emit('active-changed', false);
        if (this.clickHandler && this.map) {
          this.map.off('click', this.clickHandler);
          this.clickHandler = null;
        }
        this.cleanupMapLayers();
        this.triggerPoints = [];
        this.zoneTrackCounts = [];
        this.zoneTrackIds = [];
        this.zoneHasVisibleTracks = [];
        this.$nextTick(() => {
          this.showResults = true;
        });
      } catch (error) {
        console.error('Measure fetch failed:', error);
        this.toast.add({
          severity: 'warning',
          summary: 'Info',
          detail: 'Failed to fetch from server',
          life: 2000
        });
      } finally {
        this.isLoading = false;
      }
    },

    onMeasureClosed() {
      this.$emit('active-changed', false);
      this.$emit('tool-closed');
      if (this.clickHandler && this.map) {
        this.map.off('click', this.clickHandler);
        this.clickHandler = null;
      }
      this.cleanupMapLayers();
      this.triggerPoints = [];
      this.zoneTrackCounts = [];
      this.zoneTrackIds = [];
      this.zoneHasVisibleTracks = [];
    },

    onResultsClosed() {
      this.$emit('tool-closed');
    },

    onMaximize() {
      this.onMaximizeSender();
    },

    onMapClick(e) {
      if (!this.active || !this.map) return;

      const lngLat = e.lngLat;
      const idx = this.triggerPoints.length;
      const labelText = String.fromCharCode(65 + idx);

      const triggerPoint = {
        name: labelText,
        coordinate: {
          x: lngLat.lng,
          y: lngLat.lat,
        }
      };
      this.triggerPoints.push(triggerPoint);

      // Client-side hit-test: check if visible tracks are under this zone
      const hasVisible = this.checkVisibleTracksNearPoint(lngLat, this.radius());
      this.zoneHasVisibleTracks.push(hasVisible);
      this.zoneTrackCounts.push(undefined); // undefined = loading
      this.zoneTrackIds.push(undefined);

      const zoneColor = this.getZoneColor(idx);

      const circleSourceId = `measure-circle-${idx}`;
      const circleLayerId = `measure-circle-layer-${idx}`;
      const circleGeoJson = this.createGeoJsonCircle(lngLat.lng, lngLat.lat, this.radius());

      this.map.addSource(circleSourceId, {
        type: 'geojson',
        data: circleGeoJson,
      });
      this.map.addLayer({
        id: circleLayerId,
        type: 'fill',
        source: circleSourceId,
        paint: {
          'fill-color': zoneColor,
          'fill-opacity': 0.28,
          'fill-opacity-transition': {duration: 0, delay: 0},
        },
      });
      const circleOutlineLayerId = `measure-circle-outline-layer-${idx}`;
      this.map.addLayer({
        id: circleOutlineLayerId,
        type: 'line',
        source: circleSourceId,
        paint: {
          'line-color': zoneColor,
          'line-width': 3,
          'line-opacity': 0.88,
          'line-opacity-transition': {duration: 0, delay: 0},
        },
      });
      this.measureSourceIds.push(circleSourceId);
      this.measureLayerIds.push(circleLayerId);
      this.measureLayerIds.push(circleOutlineLayerId);

      const labelSourceId = `measure-label-${idx}`;
      const labelLayerId = `measure-label-layer-${idx}`;

      this.map.addSource(labelSourceId, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [{
            type: 'Feature',
            geometry: {type: 'Point', coordinates: [lngLat.lng, lngLat.lat]},
            properties: {label: labelText},
          }],
        },
      });
      this.map.addLayer({
        id: labelLayerId,
        type: 'symbol',
        source: labelSourceId,
        layout: {
          'text-field': ['get', 'label'],
          'text-size': 18,
          'text-font': ['Noto Sans Medium'],
          'text-anchor': 'center',
          'text-allow-overlap': true,
        },
        paint: {
          'text-color': '#10213a',
          'text-halo-color': 'rgba(255,255,255,0.94)',
          'text-halo-width': 2.4,
          'text-opacity-transition': {duration: 0, delay: 0},
        },
      });
      this.measureSourceIds.push(labelSourceId);
      this.measureLayerIds.push(labelLayerId);

      // Show toast if no visible tracks under this click
      if (!hasVisible) {
        this.toast.add({
          severity: 'warn',
          summary: 'No tracks nearby',
          detail: 'Tap closer to a recorded route for better results.',
          life: 3000,
        });
      }

      // Fire async server count for this zone
      this.fetchZoneTrackCount(idx);
    },

    updateAllCircles() {
      if (!this.map) return;
      this.triggerPoints.forEach((point, idx) => {
        const source = this.map.getSource(`measure-circle-${idx}`);
        if (source) {
          source.setData(this.createGeoJsonCircle(point.coordinate.x, point.coordinate.y, this.radius()));
        }
      });
      // Re-run client-side hit-test for all zones with new radius
      this.refreshAllZoneVisualHitTest();
    },

    /**
     * Client-side hit-test: checks if any rendered track features exist within a bounding box
     * around the given point + radius. Uses MapLibre's queryRenderedFeatures against the
     * already-rendered tracks-layer. Instant (0ms), but only sees visible/rendered tracks.
     */
    checkVisibleTracksNearPoint(lngLat, radiusMeters) {
      if (!this.map) return false;
      const trackLayerIds = ['tracks-layer', 'tracks-dot-layer', 'tracks-overview-dots'];
      const existingLayers = trackLayerIds.filter(id => this.map.getLayer(id));
      if (!existingLayers.length) return false;

      // Convert radius to approximate pixel bbox
      const center = this.map.project([lngLat.lng, lngLat.lat]);
      const edgePoint = this.map.project([
        lngLat.lng + (radiusMeters / (111320 * Math.cos(lngLat.lat * Math.PI / 180))),
        lngLat.lat,
      ]);
      const radiusPx = Math.max(Math.abs(edgePoint.x - center.x), 8);

      const bbox = [
        [center.x - radiusPx, center.y - radiusPx],
        [center.x + radiusPx, center.y + radiusPx],
      ];

      const features = this.map.queryRenderedFeatures(bbox, { layers: existingLayers });
      return features.length > 0;
    },

    refreshAllZoneVisualHitTest() {
      this.triggerPoints.forEach((point, idx) => {
        const hasVisible = this.checkVisibleTracksNearPoint(
          { lng: point.coordinate.x, lat: point.coordinate.y },
          this.radius()
        );
        this.zoneHasVisibleTracks[idx] = hasVisible;
        this.updateZoneColor(idx);
      });
    },

    getZoneVisualState(idx) {
      const count = this.zoneTrackCounts[idx];
      if (count === undefined) {
        return 'loading';
      }
      if (count === -1) {
        return 'error';
      }
      if (count === 0) {
        return 'warning';
      }
      return 'ok';
    },

    getZoneColor(idx) {
      const state = this.getZoneVisualState(idx);
      if (state === 'loading') {
        return '#64748b';
      }
      if (state === 'error') {
        return '#dc2626';
      }
      if (state === 'warning') {
        return '#ea580c';
      }
      return '#2563eb';
    },

    updateZoneColor(idx) {
      if (!this.map) return;
      const color = this.getZoneColor(idx);
      const fillLayerId = `measure-circle-layer-${idx}`;
      const outlineLayerId = `measure-circle-outline-layer-${idx}`;
      if (this.map.getLayer(fillLayerId)) {
        this.map.setPaintProperty(fillLayerId, 'fill-color', color);
        this.map.setPaintProperty(fillLayerId, 'fill-opacity', this.getZoneVisualState(idx) === 'loading' ? 0.18 : 0.28);
      }
      if (this.map.getLayer(outlineLayerId)) {
        this.map.setPaintProperty(outlineLayerId, 'line-color', color);
        this.map.setPaintProperty(outlineLayerId, 'line-width', this.getZoneVisualState(idx) === 'warning' ? 4 : 3.2);
        this.map.setPaintProperty(outlineLayerId, 'line-opacity', 0.9);
        this.map.setPaintProperty(outlineLayerId, 'line-dasharray', this.getZoneVisualState(idx) === 'loading' ? [2, 2] : [1, 0]);
      }
    },

    /**
     * Fires a server-side count request for one zone using the existing endpoint.
     */
    async fetchZoneTrackCount(idx) {
      // Cancel any previous request for this index
      if (this.zoneCountAbortControllers[idx]) {
        this.zoneCountAbortControllers[idx].abort();
      }
      const ac = new AbortController();
      this.zoneCountAbortControllers[idx] = ac;

      const point = this.triggerPoints[idx];
      if (!point) return;

      try {
        const trackIds = await fetchTrackIdsWithinDistanceOfPoint(
          point.coordinate.x,
          point.coordinate.y,
          this.radius(),
          ac.signal,
        );
        // Check zone still exists (user may have undone it)
        if (idx < this.triggerPoints.length) {
          this.zoneTrackIds[idx] = trackIds;
          this.zoneTrackCounts[idx] = trackIds.length;
          // Also update color based on server result (overrides client-side hint)
          const hasServerTracks = trackIds.length > 0;
          this.zoneHasVisibleTracks[idx] = hasServerTracks;
          this.updateZoneColor(idx);
          // Force Vue reactivity for the array
          this.zoneTrackCounts = [...this.zoneTrackCounts];
        }
      } catch (e) {
        if (e && e.name === 'CanceledError') return;
        if (e && e.message && e.message.includes('canceled')) return;
        // On error, mark as unknown
        if (idx < this.triggerPoints.length) {
          this.zoneTrackIds[idx] = null;
          this.zoneTrackCounts[idx] = -1;
          this.updateZoneColor(idx);
          this.zoneTrackCounts = [...this.zoneTrackCounts];
        }
      }
    },

    debouncedRefreshAllZoneCounts() {
      if (this.radiusCountDebounceTimer) {
        clearTimeout(this.radiusCountDebounceTimer);
      }
      this.radiusCountDebounceTimer = setTimeout(() => {
        this.triggerPoints.forEach((_, idx) => {
          this.zoneTrackIds[idx] = undefined;
          this.zoneTrackCounts[idx] = undefined; // reset to loading
          this.updateZoneColor(idx);
          this.fetchZoneTrackCount(idx);
        });
        this.zoneTrackCounts = [...this.zoneTrackCounts];
      }, 500);
    },

    createGeoJsonCircle(lng, lat, radiusMeters, points = 64) {
      const coords = [];
      const earthRadius = 6371000;
      const latRad = lat * Math.PI / 180;
      const lngRad = lng * Math.PI / 180;
      for (let i = 0; i <= points; i++) {
        const angle = (i / points) * 2 * Math.PI;
        const dLat = (radiusMeters / earthRadius) * Math.cos(angle);
        const dLng = (radiusMeters / (earthRadius * Math.cos(latRad))) * Math.sin(angle);
        coords.push([
          (lngRad + dLng) * 180 / Math.PI,
          (latRad + dLat) * 180 / Math.PI,
        ]);
      }
      return {
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          geometry: {type: 'Polygon', coordinates: [coords]},
          properties: {},
        }],
      };
    },
  },
});
</script>

<style>
.measure-map-overlay {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0px) + 0.75rem);
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  display: inline-flex;
  align-items: center;
  min-width: min(34rem, calc(100vw - 1.25rem));
  max-width: calc(100vw - 1.25rem);
  padding: 0.65rem 0.8rem;
  border-radius: 1.25rem;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.14);
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  pointer-events: none;
}

.measure-flow-rail {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.measure-flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.14rem;
  flex: 0 0 auto;
  min-width: 2.8rem;
}

.measure-flow-node-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.7rem;
  height: 1.7rem;
  border-radius: 999px;
  border: 1px solid currentColor;
  background: var(--surface-glass-heavy);
  font-size: var(--text-sm-size);
  font-weight: 800;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-value {
  color: currentColor;
  font-size: var(--text-sm-size);
  font-weight: 800;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-value--final {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: var(--text-2xs-size);
}

.measure-flow-node-detail {
  color: rgba(15, 23, 42, 0.56);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  line-height: var(--text-2xs-lh);
  text-transform: lowercase;
}

.measure-flow-node-target {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.95rem;
  height: 1.95rem;
  border-radius: 999px;
  border: 2px solid currentColor;
  background: var(--surface-glass-heavy);
  font-size: var(--text-sm-size);
  font-weight: 900;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-target::after {
  content: '';
  position: absolute;
  inset: 0.22rem;
  border-radius: 999px;
  border: 1.5px solid currentColor;
  opacity: 0.32;
}

.measure-flow-connector {
  flex: 1 1 2rem;
  min-width: 1rem;
  height: 2px;
  margin: 0 0.3rem 1.05rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.35);
}

.measure-flow-node--ok {
  color: #2563eb;
}

.measure-flow-node--loading {
  color: #64748b;
}

.measure-flow-node--warning {
  color: #ea580c;
}

.measure-flow-node--error {
  color: #dc2626;
}

.measure-flow-connector--ok {
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.55), rgba(37, 99, 235, 0.18));
}

.measure-flow-connector--loading {
  background: linear-gradient(90deg, rgba(100, 116, 139, 0.46), rgba(100, 116, 139, 0.16));
}

.measure-flow-connector--warning {
  background: linear-gradient(90deg, rgba(234, 88, 12, 0.48), rgba(234, 88, 12, 0.16));
}

.measure-flow-connector--error {
  background: linear-gradient(90deg, rgba(220, 38, 38, 0.48), rgba(220, 38, 38, 0.16));
}
</style>

<style scoped>

.measure-sheet {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  gap: 0.7rem;
  padding: 0.35rem 0.75rem calc(0.75rem + var(--safe-bottom));
  color: var(--text-secondary);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.measure-controls-card,
.measure-info-card {
  border-radius: 0;
}

.measure-stat-label,
.measure-control-label {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.measure-stat-value {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}

.measure-controls-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0;
}

.measure-controls-card--dock {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.35rem 0 0.15rem;
}

/* ── Toolbar: Undo · Clear All · Analyze ── */
.measure-toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.measure-toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.38rem;
  height: 2.55rem;
  padding: 0 0.85rem;
  border-radius: 0.85rem;
  border: none;
  background: var(--accent);
  color: var(--text-inverse);
  font-size: var(--text-sm-size);
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease, box-shadow 0.15s ease;
  white-space: nowrap;
}

.measure-toolbar-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
}

.measure-toolbar-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.measure-toolbar-btn i {
  font-size: var(--text-base-size);
}

.measure-toolbar-btn--analyze {
  margin-left: auto;
  background: var(--text-muted);
  color: var(--surface-page);
  opacity: 0.52;
}

.measure-toolbar-btn--analyze.measure-toolbar-btn--ready {
  background: var(--accent);
  color: var(--text-inverse);
  opacity: 1;
}

.measure-toolbar-btn--analyze.measure-toolbar-btn--ready:hover:not(:disabled) {
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.35);
}

/* ── Bare radius slider ── */
.measure-radius-bare {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.measure-radius-hint {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 500;
  letter-spacing: 0.02em;
  text-align: right;
  opacity: 0.72;
}

.measure-controls-header {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.measure-control-value {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 800;
}

.measure-control-note,
.measure-zone-hint {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

/* ── Placement section ── */
.measure-placement-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-top: 0.15rem;
  border-top: 1px solid var(--border-default);
}

.measure-placement-status {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}

.measure-placement-kicker {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.measure-placement-text {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  line-height: var(--text-sm-lh);
}

.measure-explanation {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 400;
  line-height: var(--text-xs-lh);
  margin: 0;
  opacity: 0.82;
}

.measure-bar-slider {
  width: 100%;
  align-self: center;
  --p-slider-handle-width: 20px;
  --p-slider-handle-height: 20px;
}

.measure-bar-slider :deep(.p-slider) {
  background: var(--slider-track);
  height: 8px;
  border-radius: 999px;
}

.measure-bar-slider :deep(.p-slider .p-slider-range) {
  background: var(--slider-gradient);
  border-radius: 999px;
}

.measure-bar-slider :deep(.p-slider .p-slider-handle) {
  background: var(--slider-handle);
  border: 2px solid var(--slider-handle-border);
  box-shadow: 0 0 0 5px var(--accent-subtle);
}

.measure-zone-section {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.measure-zone-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.measure-zone-chip,
.measure-zone-empty {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-subtle);
}

.measure-zone-chip {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.measure-zone-empty {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

@keyframes measure-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.measure-spin {
  animation: measure-spin 0.8s linear infinite;
}

@keyframes measure-overlay-pulse {
  0% {
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
  }
  50% {
    box-shadow: 0 12px 28px rgba(100, 116, 139, 0.22);
  }
  100% {
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
  }
}

@media screen and (max-width: 768px) {
  .measure-map-overlay {
    min-width: calc(100vw - 0.9rem);
    max-width: calc(100vw - 0.9rem);
    top: calc(env(safe-area-inset-top, 0px) + 0.5rem);
    padding: 0.55rem 0.6rem;
  }

  .measure-flow-node {
    min-width: 2.3rem;
  }

  .measure-flow-node-circle {
    width: 1.45rem;
    height: 1.45rem;
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-target {
    width: 1.68rem;
    height: 1.68rem;
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-value {
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-value--final {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-node-detail {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-connector {
    margin: 0 0.18rem 0.94rem;
  }

  .measure-sheet {
    gap: 0.45rem;
    padding: 0.1rem 0.2rem calc(0.5rem + var(--safe-bottom));
  }

  .measure-sheet-header {
    min-height: 1.7rem;
  }

  .measure-sheet-header-copy {
    padding-right: 2.1rem;
  }

  .measure-toolbar {
    gap: 0.35rem;
  }

  .measure-toolbar-btn {
    height: 2.4rem;
    padding: 0 0.65rem;
    font-size: var(--text-xs-size);
    border-radius: 0.75rem;
  }

  .measure-radius-bare {
    gap: 0.25rem;
  }

  .measure-radius-hint {
    font-size: var(--text-2xs-size);
  }

  .measure-placement-section {
    gap: 0.4rem;
  }

  .measure-placement-text {
    font-size: var(--text-sm-size);
  }

  .measure-explanation {
    font-size: var(--text-2xs-size);
  }

  .measure-bar-slider {
    --p-slider-handle-width: 28px;
    --p-slider-handle-height: 28px;
  }

  .measure-bar-slider :deep(.p-slider) {
    height: 12px;
  }

  .measure-bar-slider :deep(.p-slider .p-slider-handle) {
    box-shadow: 0 0 0 8px var(--accent-subtle);
  }
}

</style>
