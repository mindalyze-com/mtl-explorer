<template>
  <div class="container">
    <div class="m-0">
      <!-- Intro text to indicate whether the palette is exhausted -->

      <Message v-if="palette.isColorPaletteExhausted()" severity="warn">
        There are more groups than colors, so some colors are used more than once. Choose a palette with more colors to
        tell the groups apart.
      </Message>

      <div>The map uses the following colors</div>

      <!-- DataTable to display group-color mappings and usage counts -->
      <DataTable
        :value="groupColorData"
        class="p-datatable-gridlines my-datatable"
        scrollable
        responsive-layout="scroll"
        sort-field="group"
        :sort-order="1"
      >
        <Column field="group" header="Group" sortable></Column>
        <Column field="color" header="Color" sortable>
          <template #body="slotProps">
            <!-- Display a colored box as well as the color code -->
            <span
              :style="{
                backgroundColor: slotProps.data.color,
                width: '20px',
                height: '20px',
                display: 'inline-block',
                marginRight: '10px',
              }"
            ></span>
            <!--          {{ slotProps.data.color }}-->
          </template>
        </Column>
        <Column field="count" header="Number of tracks" sortable></Column>
      </DataTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ColorPalette } from '@/components/filter/ColorPalette';

const props = defineProps<{
  palette: ColorPalette;
}>();

const groupColorData = ref<Array<{ group: string; color: string; count: number }>>([]);

onMounted(() => {
  const groupColorMap = props.palette.getColorMap();
  const groupColorCounter = props.palette.getGroupColorCounter();

  groupColorData.value = Array.from(groupColorMap.entries()).map(([group, color]) => {
    return {
      group: group,
      color: color,
      count: groupColorCounter.get(group) || 0,
    };
  });
});
</script>

<style scoped>
.container {
  width: 100%;
  max-width: 70vw;
  padding-top: 1rem;
  padding-bottom: 1rem;
}

.p-dialog-maximized .container {
  width: 100%;
  height: 100%;
  max-width: none;
}

.my-datatable {
  padding-top: 1rem;
  width: 100%;
}

.p-datatable-gridlines {
  width: 100%;
}

/* Mobile responsive improvements */
@media screen and (max-width: 768px) {
  .container {
    max-width: 95vw;
    padding: 0.5rem;
  }

  .my-datatable {
    font-size: 0.875rem;
  }

  .my-datatable :deep(th),
  .my-datatable :deep(td) {
    padding: 0.5rem 0.25rem;
  }
}
</style>
