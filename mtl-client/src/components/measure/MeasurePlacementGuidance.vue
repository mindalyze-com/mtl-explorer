<template>
  <div class="measure-placement-guidance" role="status" aria-live="polite">
    <div class="measure-map-guidance">
      <span class="measure-map-guidance-icon" :class="guidance.toneClass" aria-hidden="true">
        <i :class="guidance.icon"></i>
      </span>
      <span class="measure-map-guidance-copy">
        <strong>{{ guidance.title }}</strong>
        <span>{{ guidance.detail }}</span>
      </span>
    </div>

    <div v-if="zoneNodes.length" class="measure-flow-rail">
      <template v-for="(node, idx) in zoneNodes" :key="node.key">
        <div class="measure-flow-node" :class="node.toneClass">
          <div class="measure-flow-node-circle">{{ node.label }}</div>
          <div class="measure-flow-node-value">{{ node.value }}</div>
          <div class="measure-flow-node-detail">{{ node.detail }}</div>
        </div>
        <div
          v-if="idx < zoneNodes.length - 1 || sharedFlowNode"
          class="measure-flow-connector"
          :class="node.connectorClass"
        ></div>
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
</template>

<script setup lang="ts">
defineProps<{
  guidance: { title: string; detail: string; icon: string; toneClass: string };
  zoneNodes: Array<{
    key: string;
    label: string;
    value: string;
    detail: string;
    toneClass: string;
    connectorClass: string;
  }>;
  sharedFlowNode: { value: string; title: string; detail: string; toneClass: string } | null;
}>();
</script>

<style scoped>
.measure-map-guidance {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
}

.measure-map-guidance-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 999px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  font-size: var(--text-base-size);
}

.measure-map-guidance-icon--active,
.measure-map-guidance-icon--ready {
  color: var(--viz-blue);
}

.measure-map-guidance-icon--loading {
  color: var(--viz-slate);
}

.measure-map-guidance-icon--warning {
  color: var(--viz-orange-strong);
}

.measure-map-guidance-icon--error {
  color: var(--viz-red);
}

.measure-map-guidance-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}

.measure-map-guidance-copy strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 750;
  line-height: var(--text-sm-lh);
}

.measure-map-guidance-copy span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.measure-flow-rail {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  margin-top: 0.55rem;
  padding-top: 0.55rem;
  border-top: 1px solid var(--border-default);
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
  color: var(--viz-blue);
}

.measure-flow-node--loading {
  color: var(--viz-slate);
}

.measure-flow-node--warning {
  color: var(--viz-orange-strong);
}

.measure-flow-node--error {
  color: var(--viz-red);
}

.measure-flow-connector--ok {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-blue) 55%, transparent),
    color-mix(in srgb, var(--viz-blue) 18%, transparent)
  );
}

.measure-flow-connector--loading {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-slate) 46%, transparent),
    color-mix(in srgb, var(--viz-slate) 16%, transparent)
  );
}

.measure-flow-connector--warning {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-orange-strong) 48%, transparent),
    color-mix(in srgb, var(--viz-orange-strong) 16%, transparent)
  );
}

.measure-flow-connector--error {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-red) 48%, transparent),
    color-mix(in srgb, var(--viz-red) 16%, transparent)
  );
}

@media screen and (max-width: 768px) {
  .measure-map-guidance {
    gap: 0.5rem;
  }

  .measure-map-guidance-icon {
    width: 1.85rem;
    height: 1.85rem;
    font-size: var(--text-sm-size);
  }

  .measure-map-guidance-copy strong {
    font-size: var(--text-xs-size);
  }

  .measure-map-guidance-copy span {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-rail {
    margin-top: 0.45rem;
    padding-top: 0.45rem;
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
}

@media screen and (max-height: 500px) and (min-width: 769px) {
  .measure-placement-guidance {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 0.8rem;
  }

  .measure-map-guidance {
    flex: 1 1 18rem;
  }

  .measure-flow-rail {
    flex: 1 1 12rem;
    margin-top: 0;
    padding-top: 0;
    padding-left: 0.8rem;
    border-top: 0;
    border-left: 1px solid var(--border-default);
  }
}
</style>
