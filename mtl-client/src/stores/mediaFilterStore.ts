import { defineStore } from 'pinia';
import { ref } from 'vue';
import { MediaTrendRequestScopeEnum } from 'x8ing-mtl-api-typescript-fetch';

/** One media selection for the map, overview, trends and period browser. */
export const useMediaFilterStore = defineStore('mediaFilter', () => {
  const scope = ref<MediaTrendRequestScopeEnum>(MediaTrendRequestScopeEnum.AllIndexed);
  return { scope };
});
