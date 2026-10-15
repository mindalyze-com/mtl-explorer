import * as maplibregl from 'maplibre-gl';
import { Protocol } from 'pmtiles';
import { createCachingPMTiles } from '@/utils/cachingPmtilesSource';
import { ensureMapterhornProtocol } from '@/utils/mapterhornProtocol';

let pmtilesProtocol: Protocol | null = null;
let pmtilesProtocolAdded = false;

export function ensurePMTilesProtocol(): Protocol {
  ensureMapterhornProtocol();
  if (!pmtilesProtocolAdded) {
    pmtilesProtocol = new Protocol();
    maplibregl.addProtocol('pmtiles', pmtilesProtocol.tile);
    pmtilesProtocolAdded = true;
  }
  return pmtilesProtocol as Protocol;
}

export function registerCachingPMTilesArchive(url: string | null | undefined): void {
  if (!url) return;
  ensurePMTilesProtocol().add(createCachingPMTiles(url));
}
