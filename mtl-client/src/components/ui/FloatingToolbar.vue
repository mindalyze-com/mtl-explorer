<template>
  <div class="ftb" :class="{ 'ftb--expanded': expanded }" @drag.stop @dblclick.stop @mousedown.stop @click="!expanded && (expanded = true)">

    <!-- Collapsed: just a menu toggle -->
    <button v-if="!expanded" class="ftb-toggle" @click="expanded = true" aria-label="Open tools">
      <i class="bi bi-grid-3x3-gap-fill"></i>
    </button>

    <!-- Expanded toolbar -->
    <transition name="ftb-pop">
      <div v-if="expanded" class="ftb-panel">
        <button class="ftb-close" @click="expanded = false" aria-label="Collapse">
          <i class="bi bi-x"></i>
        </button>
        <div class="ftb-grid">
          <button
            v-for="tool in tools"
            :key="tool.id"
            class="ftb-btn"
            :class="{ 'ftb-btn--active': activeTool === tool.id }"
            @click="onToolClick(tool.id)"
            :aria-label="tool.label"
            :title="tool.label"
          >
            <i :class="tool.icon"></i>
            <span class="ftb-label">{{ tool.label }}</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

export interface ToolDef {
  id: string;
  icon: string;
  label: string;
}

const props = defineProps<{
  tools: ToolDef[];
  activeTool?: string | null;
}>();

const emit = defineEmits<{
  (e: 'select', toolId: string): void;
}>();

const expanded = ref(false);

function onToolClick(id: string) {
  emit('select', id);
  // Collapse toolbar after selection on mobile
  if (window.innerWidth < 769) {
    expanded.value = false;
  }
}
</script>

<style scoped>
/* ─── Wrapper ─── */
.ftb {
  position: fixed;
  z-index: var(--z-toolbar);
  top: calc(1rem + var(--safe-top, 0px));
  right: calc(0.75rem + var(--safe-right, 0px));
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  pointer-events: none;
}

/* Entire wrapper is tappable when collapsed */
.ftb:not(.ftb--expanded) {
  pointer-events: auto;
  cursor: pointer;
}

/* ─── Toggle button ─── */
.ftb-toggle {
  pointer-events: auto;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 0.875rem;
  border: 1px solid var(--border-strong);
  background: var(--surface-glass-subtle);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  color: var(--text-secondary);
  font-size: 1.15rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  box-shadow: var(--shadow-sm);
}
.ftb-toggle:hover {
  background: var(--surface-glass-heavy);
  transform: scale(1.05);
}
.ftb-toggle:active {
  transform: scale(0.95);
}

/* ─── Panel wrapper ─── */
.ftb-panel {
  pointer-events: auto;
  position: relative;
  padding: 0.35rem;
  border-radius: 1rem;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass);
  backdrop-filter: var(--blur-heavy);
  -webkit-backdrop-filter: var(--blur-heavy);
  box-shadow: var(--shadow-md);
}

/* ─── 2×4 grid ─── */
.ftb-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.15rem;
}

/* ─── Close btn ─── */
.ftb-close {
  position: absolute;
  top: -0.5rem;
  right: -0.5rem;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass-heavy);
  color: var(--text-muted);
  font-size: 0.75rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  z-index: 1;
}
.ftb-close:hover {
  background: var(--error-bg);
  color: var(--error);
}

/* ─── Tool button ─── */
.ftb-btn {
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.75rem;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 1.05rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  position: relative;
}
.ftb-btn:hover {
  background: var(--surface-active);
  color: var(--text-primary);
}
.ftb-btn--active {
  background: var(--accent-subtle) !important;
  color: var(--text-primary) !important;
  box-shadow: 0 0 8px var(--accent-glow);
}

.ftb-label {
  /* Visually hidden, available for screen readers & tooltips */
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* ─── Pop transition ─── */
.ftb-pop-enter-active {
  transition: opacity 0.25s ease, transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.ftb-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.ftb-pop-enter-from {
  opacity: 0;
  transform: scale(0.8) translateY(-8px);
}
.ftb-pop-leave-to {
  opacity: 0;
  transform: scale(0.85);
}

/* ─── Wide screens ─── */
@media (min-width: 769px) {
  .ftb {
    flex-direction: row;
    align-items: flex-start;
  }

  .ftb-grid {
    gap: 0.1rem;
  }

  /* Show labels on hover as tooltips on desktop */
  .ftb-btn:hover .ftb-label {
    position: absolute;
    width: auto;
    height: auto;
    clip: auto;
    overflow: visible;
    right: calc(100% + 0.5rem);
    top: 50%;
    transform: translateY(-50%);
    background: var(--surface-glass-heavy);
    color: var(--text-secondary);
    font-size: 0.72rem;
    font-weight: 500;
    padding: 0.25rem 0.5rem;
    border-radius: 0.375rem;
    white-space: nowrap;
    pointer-events: none;
    box-shadow: var(--shadow-sm);
  }
}

/* ─── Mobile: keep vertical, smaller ─── */
@media (max-width: 768px) {
  .ftb {
    top: calc(0.5rem + var(--safe-top, 0px));
    right: calc(0.5rem + var(--safe-right, 0px));
  }
  .ftb-toggle {
    width: 3.25rem;
    height: 3.25rem;
    font-size: 1.3rem;
    border-radius: 1rem;
  }
  .ftb-btn, .ftb-close {
    width: 2.2rem;
    height: 2.2rem;
    font-size: 0.95rem;
  }
}
</style>
