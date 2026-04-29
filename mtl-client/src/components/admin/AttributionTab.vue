<template>
  <section class="panel-section">
    <div class="panel-section__header">
      <div>
        <span class="panel-section__title">Libraries and data sources</span>
        <span class="panel-section__hint">Credits for rendering, basemaps, charts, and demo data.</span>
      </div>
    </div>
    <div class="attrib-list">
      <a href="https://maplibre.org" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">MapLibre GL JS</span>
        <span class="attrib-entry-desc">Map rendering · BSD-3-Clause</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://openstreetmap.org" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">OpenStreetMap</span>
        <span class="attrib-entry-desc">Map data · © ODbL contributors</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://protomaps.com" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">Protomaps Basemaps</span>
        <span class="attrib-entry-desc">Vector tiles · BSD-3-Clause</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://github.com/protomaps/PMTiles" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">PMTiles</span>
        <span class="attrib-entry-desc">Tile archive format · BSD-3-Clause</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://github.com/tilezen/joerd/blob/master/docs/attribution.md" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">Terrarium DEM</span>
        <span class="attrib-entry-desc">Elevation tiles · USGS · NGA · NASA</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://www.swisstopo.admin.ch" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">swisstopo</span>
        <span class="attrib-entry-desc">Swiss national maps · © swisstopo</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://www.schweizmobil.ch" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">SchweizMobil</span>
        <span class="attrib-entry-desc">Trail overlays · © SchweizMobil</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://waymarkedtrails.org" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">Waymarked Trails</span>
        <span class="attrib-entry-desc">Worldwide route overlays · OSM data · CC BY-SA</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://www.highcharts.com" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">Highcharts</span>
        <span class="attrib-entry-desc">Charts · Non-commercial use</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://www.gpsbabel.org" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">GPSBabel</span>
        <span class="attrib-entry-desc">GPS file format conversion · GPL-2.0</span>
        <i class="pi pi-external-link"/>
      </a>
      <a href="https://brouter.de" target="_blank" rel="noopener noreferrer" class="attrib-entry">
        <span class="attrib-entry-name">BRouter</span>
        <span class="attrib-entry-desc">Offline bike routing · MIT</span>
        <i class="pi pi-external-link"/>
      </a>
      <template v-if="isDemoMode">
        <a href="https://doi.org/10.24432/C55W25" target="_blank" rel="noopener noreferrer" class="attrib-entry">
          <span class="attrib-entry-name">Porto Taxi GPS Dataset</span>
          <span class="attrib-entry-desc">Demo tracks · CC BY 4.0</span>
          <i class="pi pi-external-link"/>
        </a>
      </template>
    </div>
  </section>
</template>

<script>
export default {
  name: 'AttributionTab',
  props: {
    isDemoMode: {
      type: Boolean,
      default: false,
    },
  },
};
</script>

<style scoped>
.attrib-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.attrib-entry {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-elevated);
  transition: color 0.15s, border-color 0.15s;
}

.attrib-entry:hover {
  color: var(--accent-text);
  border-color: var(--border-hover);
}

.attrib-entry-name {
  font-weight: 600;
  flex-shrink: 0;
}

.attrib-entry-desc {
  font-size: var(--text-xs-size);
  color: var(--text-faint);
  flex: 1;
}

.attrib-entry .pi {
  font-size: var(--text-2xs-size);
  color: var(--text-faint);
  flex-shrink: 0;
}
</style>
