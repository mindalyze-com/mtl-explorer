import { describe, expect, it } from 'vitest';
import {
  MapConfigDtoTileModeEnum,
  type LocationSearchStatusDto,
  type MapConfigDto,
  type MapServerStatusDto,
} from 'x8ing-mtl-api-typescript-fetch';
import {
  buildAdminOperationalTasks,
  normalizeBRouterTask,
  normalizeLocationSearchTask,
  normalizeMapTask,
} from '@/utils/adminOperationalTasks';

const localMapConfig: MapConfigDto = {
  tileMode: MapConfigDtoTileModeEnum.Local,
  plannerEnabled: true,
};

describe('admin operational task normalization', () => {
  it('maps vector map downloads to determinate progress', () => {
    const task = normalizeMapTask(
      {
        phase: 'downloading',
        ready: false,
        downloadPct: 42.5,
        downloadBytes: 50 * 1024 * 1024,
        downloadTotal: 100 * 1024 * 1024,
        message: 'Downloading planet file',
      } satisfies MapServerStatusDto,
      localMapConfig
    );

    expect(task).toMatchObject({
      label: 'Vector Map Tiles',
      state: 'running',
      active: true,
      indeterminate: false,
      progressPercent: 43,
      metric: '50 MB / 100 MB',
    });
  });

  it('preserves external component version info on operational tasks', () => {
    const task = normalizeMapTask(
      {
        phase: 'ready',
        ready: true,
        archiveId: '20260524',
        versionInfo: {
          image: {
            version: '1.62',
            buildTime: '2026-05-24T12:34:56Z',
          },
          components: {
            pmtiles: '1.30.2',
          },
          data: {
            protomapsArchive: '20260524',
          },
        },
      } satisfies MapServerStatusDto,
      localMapConfig
    );

    expect(task.versionInfo?.image?.version).toBe('1.62');
    expect(task.versionInfo?.components?.pmtiles).toBe('1.30.2');
    expect(task.versionInfo?.data?.protomapsArchive).toBe('20260524');
  });

  it('maps vector map extraction to active indeterminate progress', () => {
    const task = normalizeMapTask(
      {
        phase: 'extracting',
        ready: false,
        message: 'Extracting low-zoom tiles',
      } satisfies MapServerStatusDto,
      localMapConfig
    );

    expect(task).toMatchObject({
      state: 'running',
      statusLabel: 'extracting',
      active: true,
      indeterminate: true,
      progressPercent: null,
    });
  });

  it.each(['queued', 'optimizing'])(
    'maps location search %s status to indeterminate GeoNames preparation',
    (phase) => {
      const task = normalizeLocationSearchTask({
        phase,
        ready: false,
        message: 'Preparing GeoNames search database',
      } satisfies LocationSearchStatusDto);

      expect(task).toMatchObject({
        label: 'Location Search',
        state: 'running',
        active: true,
        indeterminate: true,
        progressPercent: null,
        metric: '',
      });
    }
  );

  it('summarizes ready GeoNames location search row counts', () => {
    const task = normalizeLocationSearchTask({
      phase: 'ready',
      ready: true,
      rowCount: 300,
      populatedPlaceCount: 220,
      terrainCount: 80,
      sourceAttribution: 'GeoNames',
    } satisfies LocationSearchStatusDto);

    expect(task).toMatchObject({
      label: 'Location Search',
      state: 'done',
      metric: '300 places (220 populated, 80 terrain)',
    });
  });

  it('maps unavailable location search to an inactive warning row', () => {
    const task = normalizeLocationSearchTask({
      phase: 'unavailable',
      ready: false,
      message: 'Location search sidecar is not reachable.',
    } satisfies LocationSearchStatusDto);

    expect(task).toMatchObject({
      label: 'Location Search',
      state: 'warning',
      statusLabel: 'unavailable',
      active: false,
      indeterminate: false,
      progressPercent: null,
      detail: 'Location search sidecar is not reachable.',
    });
  });

  it('maps BRouter queued and in-progress segments to active indeterminate progress', () => {
    const task = normalizeBRouterTask({
      available: true,
      brouterRunning: true,
      segmentsOnDisk: 4,
      segmentsQueued: 2,
      segmentsInProgress: ['E5_N45.rd5'],
    });

    expect(task).toMatchObject({
      label: 'Routing Segments',
      state: 'running',
      active: true,
      indeterminate: true,
      progressPercent: null,
      metric: '2 queued, 1 downloading',
    });
  });

  it('hides routing segments when the planner is disabled', () => {
    const tasks = buildAdminOperationalTasks({
      mapConfig: { tileMode: MapConfigDtoTileModeEnum.Local, plannerEnabled: false },
      mapStatus: { phase: 'ready', ready: true },
      locationSearchStatus: { phase: 'ready', ready: true },
      brouterStatus: {
        available: true,
        brouterRunning: true,
        segmentsQueued: 1,
      },
    });

    expect(tasks.map((task) => task.label)).not.toContain('Routing Segments');
  });

  it('keeps unreachable sidecars as warning rows without dropping other tasks', () => {
    const tasks = buildAdminOperationalTasks({
      mapConfig: localMapConfig,
      mapStatus: null,
      locationSearchStatus: {
        phase: 'unreachable',
        ready: false,
        message: 'Location search status is not reachable.',
      },
      brouterStatus: {
        available: false,
        reason: 'unreachable',
      },
    });

    expect(tasks).toHaveLength(3);
    expect(tasks.map((task) => task.state)).toEqual(['warning', 'warning', 'warning']);
    expect(tasks.every((task) => task.active === false)).toBe(true);
  });
});
