import maplibregl from 'maplibre-gl';
import { TERRAIN_DEM_SOURCE_ID } from '@/utils/mapStyle';
import { TRACK_COLOR, TRACK_SELECTED_COLOR } from '@/utils/trackColors';
import { sampleReplayPath, type ReplayPath, type ReplayPathPoint } from '@/components/replay/trackReplayPath';
import { shouldReplaceElevationRefreshTimer } from '@/components/map/terrainElevationScheduler';
import { normalizeLongitude, shortestLongitudeDelta, unwrapLngLatCoordinates } from '@/components/map/mapGeometry';

export const TRACK_REPLAY_LAYER_ID = 'track-replay-layer';

const REPLAY_GROUND_CLEARANCE_METERS = 8;
const FLOATS_PER_VERTEX = 11;
const BYTES_PER_FLOAT = 4;
const VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT;
const ELEVATION_REFRESH_DEBOUNCE_MS = 250;
const ELEVATION_MOVE_REFRESH_INTERVAL_MS = 250;
const ELEVATION_QUERY_BUDGET_MS = 6;
const ELEVATION_QUERY_MAX_POINTS_PER_CHUNK = 128;
const ELEVATION_VIEWPORT_PADDING_RATIO = 0.35;
const TERRAIN_CACHE_COORDINATE_PRECISION = 6;
const TERRAIN_DEM_MISSING_ELEVATION_METERS = 0;
const TERRAIN_DEM_ZERO_EPSILON_METERS = 0.05;
const TERRAIN_SOURCE_ELEVATION_ZERO_GUARD_METERS = 20;
const PLAYHEAD_INNER_RADIUS_RATIO = 0.23;
const PLAYHEAD_FILL_RADIUS_RATIO = 0.35;
const PLAYHEAD_BORDER_RADIUS_RATIO = 0.43;
const PLAYHEAD_EDGE_SOFTNESS_RATIO = 0.035;

type NormalizedColor = [number, number, number, number];

type ReplayLayerPoint = {
  lng: number;
  lat: number;
  sourceLng: number;
  x: number;
  y: number;
  z: number;
  sourceElevationMeters: number | null;
  distanceMeters: number;
  cacheKey: string;
  terrainAligned: boolean;
};

type ReplayLayerSegment = {
  start: ReplayLayerPoint;
  end: ReplayLayerPoint;
};

type ReplayProgram = {
  program: WebGLProgram;
  attributes: {
    pos: number;
    other: number;
    side: number;
    color: number;
  };
  uniforms: {
    matrix: WebGLUniformLocation | null;
    viewport: WebGLUniformLocation | null;
    halfWidth: WebGLUniformLocation | null;
    opacity: WebGLUniformLocation | null;
  };
};

type ReplayPointProgram = {
  program: WebGLProgram;
  attributes: {
    pos: number;
  };
  uniforms: {
    matrix: WebGLUniformLocation | null;
    size: WebGLUniformLocation | null;
    opacity: WebGLUniformLocation | null;
    innerRadius: WebGLUniformLocation | null;
    fillRadius: WebGLUniformLocation | null;
    borderRadius: WebGLUniformLocation | null;
    edgeSoftness: WebGLUniformLocation | null;
    innerColor: WebGLUniformLocation | null;
    fillColor: WebGLUniformLocation | null;
    borderColor: WebGLUniformLocation | null;
    outlineColor: WebGLUniformLocation | null;
  };
};

export class TrackReplayLayer implements maplibregl.CustomLayerInterface {
  id = TRACK_REPLAY_LAYER_ID;
  type = 'custom' as const;
  renderingMode = '3d' as const;

  private map: maplibregl.Map | null = null;
  private gl: WebGLRenderingContext | WebGL2RenderingContext | null = null;
  private program: ReplayProgram | null = null;
  private pointProgram: ReplayPointProgram | null = null;
  private fullBuffer: WebGLBuffer | null = null;
  private progressBuffer: WebGLBuffer | null = null;
  private playheadBuffer: WebGLBuffer | null = null;
  private fullData = new Float32Array();
  private progressData = new Float32Array();
  private playheadData = new Float32Array();
  private fullVertexCount = 0;
  private progressVertexCount = 0;
  private playheadVertexCount = 0;
  private path: ReplayPath | null = null;
  private points: ReplayLayerPoint[] = [];
  private segments: ReplayLayerSegment[] = [];
  private elevationCache = new Map<string, number>();
  private progress = 0;
  private visible = true;
  private opacity = 1;
  private fullLineWidth = 4;
  private progressLineWidth = 7;
  private playheadPointSize = 22;
  private buffersDirty = false;
  private elevationRefreshTimer: ReturnType<typeof setTimeout> | null = null;
  private elevationRefreshTimerDelayMs: number | null = null;
  private elevationRefreshGeneration = 0;
  private lastMoveElevationRefreshMs = 0;
  private readonly matrix = new Float32Array(16);
  private readonly progressColor = parseColor(TRACK_SELECTED_COLOR || TRACK_COLOR, 1);
  private readonly playheadInnerColor: NormalizedColor = [0.96, 0.62, 0.04, 1];
  private readonly playheadFillColor: NormalizedColor = [1, 0.7, 0, 0.2];
  private readonly playheadBorderColor: NormalizedColor = [1, 1, 1, 0.95];
  private readonly playheadOutlineColor: NormalizedColor = [0.96, 0.62, 0.04, 0.95];

  private readonly onTerrainData = (event?: { sourceId?: string }) => {
    if (!event?.sourceId || event.sourceId === TERRAIN_DEM_SOURCE_ID) {
      this.scheduleElevationRefresh();
    }
  };

  private readonly onMoveEnd = () => {
    this.scheduleElevationRefresh();
  };

  private readonly onMove = () => {
    const now = performance.now();
    if (now - this.lastMoveElevationRefreshMs < ELEVATION_MOVE_REFRESH_INTERVAL_MS) return;
    this.lastMoveElevationRefreshMs = now;
    this.scheduleElevationRefresh(0);
  };

  onAdd(map: maplibregl.Map, gl: WebGLRenderingContext | WebGL2RenderingContext): void {
    this.map = map;
    this.gl = gl;
    this.program = createProgram(gl);
    this.pointProgram = createPointProgram(gl);
    this.fullBuffer = gl.createBuffer();
    this.progressBuffer = gl.createBuffer();
    this.playheadBuffer = gl.createBuffer();
    this.uploadBuffers();

    map.on('sourcedata', this.onTerrainData);
    map.on('terrain', this.onTerrainData);
    map.on('move', this.onMove);
    map.on('moveend', this.onMoveEnd);
    this.scheduleElevationRefresh(0);
  }

  onRemove(map: maplibregl.Map, gl: WebGLRenderingContext | WebGL2RenderingContext): void {
    this.elevationRefreshGeneration += 1;
    this.clearElevationRefreshTimer();
    map.off('sourcedata', this.onTerrainData);
    map.off('terrain', this.onTerrainData);
    map.off('move', this.onMove);
    map.off('moveend', this.onMoveEnd);
    if (this.fullBuffer) gl.deleteBuffer(this.fullBuffer);
    if (this.progressBuffer) gl.deleteBuffer(this.progressBuffer);
    if (this.playheadBuffer) gl.deleteBuffer(this.playheadBuffer);
    if (this.program?.program) gl.deleteProgram(this.program.program);
    if (this.pointProgram?.program) gl.deleteProgram(this.pointProgram.program);
    this.fullBuffer = null;
    this.progressBuffer = null;
    this.playheadBuffer = null;
    this.program = null;
    this.pointProgram = null;
    this.gl = null;
    this.map = null;
  }

  setData(path: ReplayPath): void {
    this.path = path;
    const unwrappedCoordinates = unwrapLngLatCoordinates(
      path.points.map((point) => [point.lng, point.lat] as [number, number])
    );
    this.points = path.points.map((point, index) => replayPathPointToLayerPoint(point, unwrappedCoordinates[index][0]));
    this.segments = [];
    for (let i = 1; i < this.points.length; i += 1) {
      this.segments.push({ start: this.points[i - 1], end: this.points[i] });
    }
    this.applyCachedElevations();
    this.rebuildVertexData();
    this.scheduleElevationRefresh(0);
  }

  setProgress(progress: number): void {
    const nextProgress = Number.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
    if (Math.abs(nextProgress - this.progress) < 0.0005) return;
    this.progress = nextProgress;
    this.rebuildVertexData();
  }

  setVisible(visible: boolean): void {
    this.visible = visible;
    if (visible) this.scheduleElevationRefresh(0);
    this.map?.triggerRepaint();
  }

  setOpacity(opacity: number): void {
    this.opacity = Math.max(0, Math.min(1, opacity));
    this.map?.triggerRepaint();
  }

  render(
    gl: WebGLRenderingContext | WebGL2RenderingContext,
    options: { defaultProjectionData: { mainMatrix: ArrayLike<number> } }
  ): void {
    if (
      !this.visible ||
      !this.program ||
      !this.fullBuffer ||
      (this.fullVertexCount === 0 && this.progressVertexCount === 0 && this.playheadVertexCount === 0)
    ) {
      return;
    }
    if (this.buffersDirty) this.uploadBuffers();

    copyMatrix(options.defaultProjectionData.mainMatrix, this.matrix);

    gl.useProgram(this.program.program);
    gl.uniformMatrix4fv(this.program.uniforms.matrix, false, this.matrix);
    gl.uniform2f(this.program.uniforms.viewport, gl.drawingBufferWidth, gl.drawingBufferHeight);
    gl.uniform1f(this.program.uniforms.opacity, this.opacity);

    const depthTestEnabled = gl.isEnabled(gl.DEPTH_TEST);
    const depthMaskEnabled = Boolean(gl.getParameter(gl.DEPTH_WRITEMASK));

    gl.enable(gl.BLEND);
    gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    gl.disable(gl.CULL_FACE);
    gl.disable(gl.DEPTH_TEST);
    gl.depthMask(false);

    const pixelRatio = this.pixelRatio(gl);
    if (this.fullVertexCount > 0) {
      drawBuffer(gl, this.program, this.fullBuffer, this.fullVertexCount, (this.fullLineWidth * pixelRatio) / 2);
    }
    if (this.progressBuffer && this.progressVertexCount > 0) {
      drawBuffer(
        gl,
        this.program,
        this.progressBuffer,
        this.progressVertexCount,
        (this.progressLineWidth * pixelRatio) / 2
      );
    }
    if (this.pointProgram && this.playheadBuffer && this.playheadVertexCount > 0) {
      drawPlayheadPoint(gl, this.pointProgram, this.playheadBuffer, this.matrix, {
        opacity: this.opacity,
        pointSize: this.playheadPointSize * pixelRatio,
        innerColor: this.playheadInnerColor,
        fillColor: this.playheadFillColor,
        borderColor: this.playheadBorderColor,
        outlineColor: this.playheadOutlineColor,
      });
    }

    gl.depthMask(depthMaskEnabled);
    if (depthTestEnabled) gl.enable(gl.DEPTH_TEST);
  }

  private rebuildVertexData(): void {
    const full: number[] = [];
    const progress: number[] = [];
    const progressDistance = (this.path?.totalDistanceMeters ?? 0) * this.progress;
    const playheadPoint = replayLayerPointAtDistance(this.points, this.segments, progressDistance);

    for (const segment of this.segments) {
      if (progressDistance <= segment.start.distanceMeters) continue;
      if (progressDistance >= segment.end.distanceMeters) {
        pushSegment(progress, segment.start, segment.end, this.progressColor);
      } else {
        const clippedEnd = interpolateLayerPoint(segment.start, segment.end, progressDistance);
        pushSegment(progress, segment.start, clippedEnd, this.progressColor);
      }
    }

    this.fullData = new Float32Array(full);
    this.progressData = new Float32Array(progress);
    this.playheadData = playheadPoint
      ? new Float32Array([playheadPoint.x, playheadPoint.y, playheadPoint.z])
      : new Float32Array();
    this.fullVertexCount = this.fullData.length / FLOATS_PER_VERTEX;
    this.progressVertexCount = this.progressData.length / FLOATS_PER_VERTEX;
    this.playheadVertexCount = this.playheadData.length / 3;
    this.buffersDirty = true;
    this.uploadBuffers();
    this.map?.triggerRepaint();
  }

  private uploadBuffers(): void {
    if (!this.gl || !this.fullBuffer || !this.progressBuffer || !this.playheadBuffer) return;
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.fullBuffer);
    this.gl.bufferData(this.gl.ARRAY_BUFFER, this.fullData, this.gl.STATIC_DRAW);
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.progressBuffer);
    this.gl.bufferData(this.gl.ARRAY_BUFFER, this.progressData, this.gl.STATIC_DRAW);
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.playheadBuffer);
    this.gl.bufferData(this.gl.ARRAY_BUFFER, this.playheadData, this.gl.STATIC_DRAW);
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, null);
    this.buffersDirty = false;
  }

  private pixelRatio(gl: WebGLRenderingContext | WebGL2RenderingContext): number {
    const canvas = this.map?.getCanvas();
    const cssWidth = canvas?.clientWidth ?? 0;
    if (cssWidth > 0) return gl.drawingBufferWidth / cssWidth;
    return window.devicePixelRatio || 1;
  }

  private scheduleElevationRefresh(delayMs = ELEVATION_REFRESH_DEBOUNCE_MS): void {
    if (!this.map || !this.visible || this.points.length === 0) return;
    const normalizedDelayMs = Math.max(0, delayMs);
    if (
      this.elevationRefreshTimer &&
      !shouldReplaceElevationRefreshTimer(this.elevationRefreshTimerDelayMs, normalizedDelayMs)
    ) {
      return;
    }

    this.elevationRefreshGeneration += 1;
    this.clearElevationRefreshTimer();
    this.elevationRefreshTimerDelayMs = normalizedDelayMs;
    this.elevationRefreshTimer = setTimeout(() => {
      this.elevationRefreshTimer = null;
      this.elevationRefreshTimerDelayMs = null;
      this.startElevationRefresh();
    }, normalizedDelayMs);
  }

  private startElevationRefresh(): void {
    const terrainMap = this.terrainMap();
    if (!terrainMap || !this.visible) return;

    const candidates = this.visibleElevationCandidates();
    if (candidates.length === 0) return;

    this.elevationRefreshGeneration += 1;
    this.processElevationChunk(candidates, 0, this.elevationRefreshGeneration, []);
  }

  private processElevationChunk(
    candidates: ReplayLayerPoint[],
    startIndex: number,
    generation: number,
    updates: Array<[ReplayLayerPoint, number]>
  ): void {
    if (generation !== this.elevationRefreshGeneration) return;
    const terrainMap = this.terrainMap();
    if (!terrainMap || !this.visible) return;

    const startTime = performance.now();
    let index = startIndex;
    let queried = 0;

    while (
      index < candidates.length &&
      queried < ELEVATION_QUERY_MAX_POINTS_PER_CHUNK &&
      performance.now() - startTime < ELEVATION_QUERY_BUDGET_MS
    ) {
      const point = candidates[index];
      index += 1;

      const cached = this.elevationCache.get(point.cacheKey);
      if (Number.isFinite(cached)) {
        updates.push([point, cached as number]);
        continue;
      }

      queried += 1;
      const elevation = terrainMap.queryTerrainElevation([point.sourceLng, point.lat]);
      if (!shouldUseTerrainElevation(point, elevation)) continue;
      this.elevationCache.set(point.cacheKey, elevation as number);
      updates.push([point, elevation as number]);
    }

    if (index < candidates.length) {
      setTimeout(() => this.processElevationChunk(candidates, index, generation, updates), 0);
      return;
    }

    let hasChanges = false;
    for (const [point, elevationMeters] of updates) {
      hasChanges = setPointElevation(point, elevationMeters) || hasChanges;
    }
    if (hasChanges) this.rebuildVertexData();
  }

  private visibleElevationCandidates(): ReplayLayerPoint[] {
    const bounds = this.map?.getBounds();
    if (!bounds) return [];

    const west = bounds.getWest();
    const east = bounds.getEast();
    const south = bounds.getSouth();
    const north = bounds.getNorth();
    const lngPadding = Math.max(0.01, Math.abs(east - west) * ELEVATION_VIEWPORT_PADDING_RATIO);
    const latPadding = Math.max(0.01, Math.abs(north - south) * ELEVATION_VIEWPORT_PADDING_RATIO);
    const paddedWest = west - lngPadding;
    const paddedEast = east + lngPadding;
    const paddedSouth = south - latPadding;
    const paddedNorth = north + latPadding;

    return this.points.filter(
      (point) =>
        !point.terrainAligned &&
        point.lng >= paddedWest &&
        point.lng <= paddedEast &&
        point.lat >= paddedSouth &&
        point.lat <= paddedNorth
    );
  }

  private applyCachedElevations(): void {
    for (const point of this.points) {
      const cached = this.elevationCache.get(point.cacheKey);
      if (shouldUseTerrainElevation(point, cached)) {
        setPointElevation(point, cached as number);
      } else if (cached != null) {
        this.elevationCache.delete(point.cacheKey);
      }
    }
  }

  private terrainMap():
    | (maplibregl.Map & {
        getTerrain?: () => unknown;
        queryTerrainElevation?: (lngLat: [number, number]) => number | null;
      })
    | null {
    const terrainMap = this.map as
      | (maplibregl.Map & {
          getTerrain?: () => unknown;
          queryTerrainElevation?: (lngLat: [number, number]) => number | null;
        })
      | null;
    if (!terrainMap?.getTerrain?.() || !terrainMap.queryTerrainElevation) return null;
    return terrainMap;
  }

  private clearElevationRefreshTimer(): void {
    if (this.elevationRefreshTimer) {
      clearTimeout(this.elevationRefreshTimer);
      this.elevationRefreshTimer = null;
    }
    this.elevationRefreshTimerDelayMs = null;
  }
}

export function buildReplayLayerGeojson(path: ReplayPath, progress: number): GeoJSON.FeatureCollection {
  const safeProgress = Number.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
  const sample = sampleReplayPath(path, safeProgress);
  const completedDistance = sample?.distanceMeters ?? safeProgress * path.totalDistanceMeters;
  const unwrappedPathPoints = unwrapReplayPathPoints(path.points);
  const completedCoordinates = path.points
    .map((point, index) => ({ point, unwrapped: unwrappedPathPoints[index] }))
    .filter(({ point }) => point.distanceMeters <= completedDistance)
    .map(({ point, unwrapped }) => pathPointToCoordinate(point, unwrapped));
  const unwrappedSample = sample ? unwrapReplaySample(sample, unwrappedPathPoints) : null;
  if (sample && unwrappedSample && completedCoordinates.length === 0) {
    completedCoordinates.push(pathPointToCoordinate(sample, unwrappedSample));
  } else if (
    sample &&
    unwrappedSample &&
    completedCoordinates[completedCoordinates.length - 1]?.[0] !== unwrappedSample[0]
  ) {
    completedCoordinates.push(pathPointToCoordinate(sample, unwrappedSample));
  }

  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { type: 'full' },
        geometry: {
          type: 'LineString',
          coordinates: path.points
            .map((point, index) => ({ point, unwrapped: unwrappedPathPoints[index] }))
            .map(({ point, unwrapped }) => pathPointToCoordinate(point, unwrapped)),
        },
      },
      {
        type: 'Feature',
        properties: { type: 'completed' },
        geometry: { type: 'LineString', coordinates: completedCoordinates },
      },
      ...(sample && unwrappedSample
        ? [
            {
              type: 'Feature' as const,
              properties: { type: 'playhead' },
              geometry: {
                type: 'Point' as const,
                coordinates: pathPointToCoordinate(sample, unwrappedSample),
              },
            },
          ]
        : []),
    ],
  };
}

function unwrapReplayPathPoints(points: ReplayPathPoint[]): [number, number][] {
  return unwrapLngLatCoordinates(points.map((point) => [point.lng, point.lat] as [number, number]));
}

function unwrapReplaySample(sample: ReplayPathPoint, unwrappedPathPoints: [number, number][]): [number, number] | null {
  if (unwrappedPathPoints.length === 0) return null;
  const nearest = unwrappedPathPoints.reduce((best, candidate) => {
    const bestDelta = Math.abs(shortestLongitudeDelta(best[0], sample.lng));
    const candidateDelta = Math.abs(shortestLongitudeDelta(candidate[0], sample.lng));
    return candidateDelta < bestDelta ? candidate : best;
  }, unwrappedPathPoints[0]);
  return [nearest[0] + shortestLongitudeDelta(nearest[0], sample.lng), sample.lat];
}

function replayPathPointToLayerPoint(point: ReplayPathPoint, displayLng = point.lng): ReplayLayerPoint {
  const mercator = maplibregl.MercatorCoordinate.fromLngLat(
    { lng: displayLng, lat: point.lat },
    (point.elevation ?? 0) + REPLAY_GROUND_CLEARANCE_METERS
  );
  return {
    lng: displayLng,
    lat: point.lat,
    sourceLng: normalizeLongitude(point.lng),
    x: mercator.x,
    y: mercator.y,
    z: mercator.z,
    sourceElevationMeters: point.elevation,
    distanceMeters: point.distanceMeters,
    cacheKey: coordinateCacheKey(normalizeLongitude(point.lng), point.lat),
    terrainAligned: false,
  };
}

function interpolateLayerPoint(
  start: ReplayLayerPoint,
  end: ReplayLayerPoint,
  distanceMeters: number
): ReplayLayerPoint {
  const span = Math.max(end.distanceMeters - start.distanceMeters, Number.EPSILON);
  const t = Math.max(0, Math.min(1, (distanceMeters - start.distanceMeters) / span));
  return {
    lng: interpolateNumber(start.lng, end.lng, t),
    lat: interpolateNumber(start.lat, end.lat, t),
    sourceLng: normalizeLongitude(interpolateNumber(start.lng, end.lng, t)),
    x: interpolateNumber(start.x, end.x, t),
    y: interpolateNumber(start.y, end.y, t),
    z: interpolateNumber(start.z, end.z, t),
    sourceElevationMeters: interpolateNullable(start.sourceElevationMeters, end.sourceElevationMeters, t),
    distanceMeters,
    cacheKey: coordinateCacheKey(
      normalizeLongitude(interpolateNumber(start.lng, end.lng, t)),
      interpolateNumber(start.lat, end.lat, t)
    ),
    terrainAligned: start.terrainAligned && end.terrainAligned,
  };
}

function replayLayerPointAtDistance(
  points: ReplayLayerPoint[],
  segments: ReplayLayerSegment[],
  distanceMeters: number
): ReplayLayerPoint | null {
  if (points.length === 0) return null;
  if (segments.length === 0 || distanceMeters <= points[0].distanceMeters) return points[0];
  const last = points[points.length - 1];
  if (distanceMeters >= last.distanceMeters) return last;

  const segment = segments.find(
    (candidate) => distanceMeters >= candidate.start.distanceMeters && distanceMeters <= candidate.end.distanceMeters
  );
  if (!segment) return last;
  return interpolateLayerPoint(segment.start, segment.end, distanceMeters);
}

function setPointElevation(point: ReplayLayerPoint, elevationMeters: number): boolean {
  const mercator = maplibregl.MercatorCoordinate.fromLngLat(
    { lng: point.lng, lat: point.lat },
    elevationMeters + REPLAY_GROUND_CLEARANCE_METERS
  );
  if (Math.abs(point.z - mercator.z) < 1e-10) {
    point.terrainAligned = true;
    return false;
  }
  point.z = mercator.z;
  point.terrainAligned = true;
  return true;
}

function shouldUseTerrainElevation(
  point: Pick<ReplayLayerPoint, 'sourceElevationMeters'>,
  elevationMeters: unknown
): boolean {
  if (!Number.isFinite(elevationMeters)) return false;
  const elevation = elevationMeters as number;
  if (Math.abs(elevation - TERRAIN_DEM_MISSING_ELEVATION_METERS) > TERRAIN_DEM_ZERO_EPSILON_METERS) return true;
  const sourceElevation = point.sourceElevationMeters;
  return sourceElevation != null && Math.abs(sourceElevation) <= TERRAIN_SOURCE_ELEVATION_ZERO_GUARD_METERS;
}

function pathPointToCoordinate(point: ReplayPathPoint, unwrapped?: [number, number]): number[] {
  return [unwrapped?.[0] ?? point.lng, unwrapped?.[1] ?? point.lat, point.elevation ?? 0];
}

function coordinateCacheKey(lng: number, lat: number): string {
  return `${lng.toFixed(TERRAIN_CACHE_COORDINATE_PRECISION)},${lat.toFixed(TERRAIN_CACHE_COORDINATE_PRECISION)}`;
}

function interpolateNullable(a: number | null, b: number | null, t: number): number | null {
  if (a == null && b == null) return null;
  if (a == null) return b;
  if (b == null) return a;
  return interpolateNumber(a, b, t);
}

function interpolateNumber(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

function parseColor(color: string | null | undefined, alpha: number): NormalizedColor {
  const fallback: NormalizedColor = [0.31, 0.31, 0.78, alpha];
  if (!color) return fallback;
  const normalized = color.trim();
  const match = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(normalized);
  if (!match) return fallback;
  const hex = match[1].length === 3 ? match[1].replace(/./g, (ch) => ch + ch) : match[1];
  return [
    Number.parseInt(hex.slice(0, 2), 16) / 255,
    Number.parseInt(hex.slice(2, 4), 16) / 255,
    Number.parseInt(hex.slice(4, 6), 16) / 255,
    alpha,
  ];
}

function pushSegment(target: number[], start: ReplayLayerPoint, end: ReplayLayerPoint, color: NormalizedColor): void {
  pushVertex(target, start, end, -1, color);
  pushVertex(target, start, end, 1, color);
  pushVertex(target, end, start, -1, color);
  pushVertex(target, start, end, 1, color);
  pushVertex(target, end, start, 1, color);
  pushVertex(target, end, start, -1, color);
}

function pushVertex(
  target: number[],
  point: ReplayLayerPoint,
  other: ReplayLayerPoint,
  side: number,
  color: NormalizedColor
): void {
  target.push(point.x, point.y, point.z, other.x, other.y, other.z, side, color[0], color[1], color[2], color[3]);
}

function copyMatrix(source: ArrayLike<number>, target: Float32Array): void {
  for (let i = 0; i < 16; i += 1) target[i] = source[i];
}

function drawBuffer(
  gl: WebGLRenderingContext | WebGL2RenderingContext,
  program: ReplayProgram,
  buffer: WebGLBuffer,
  vertexCount: number,
  halfWidth: number
): void {
  gl.uniform1f(program.uniforms.halfWidth, halfWidth);
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  enableAttribute(gl, program.attributes.pos, 3, 0);
  enableAttribute(gl, program.attributes.other, 3, 3 * BYTES_PER_FLOAT);
  enableAttribute(gl, program.attributes.side, 1, 6 * BYTES_PER_FLOAT);
  enableAttribute(gl, program.attributes.color, 4, 7 * BYTES_PER_FLOAT);
  gl.drawArrays(gl.TRIANGLES, 0, vertexCount);
}

function enableAttribute(
  gl: WebGLRenderingContext | WebGL2RenderingContext,
  location: number,
  size: number,
  offset: number
): void {
  if (location < 0) return;
  gl.enableVertexAttribArray(location);
  gl.vertexAttribPointer(location, size, gl.FLOAT, false, VERTEX_STRIDE_BYTES, offset);
}

function drawPlayheadPoint(
  gl: WebGLRenderingContext | WebGL2RenderingContext,
  program: ReplayPointProgram,
  buffer: WebGLBuffer,
  matrix: Float32Array,
  options: {
    opacity: number;
    pointSize: number;
    innerColor: NormalizedColor;
    fillColor: NormalizedColor;
    borderColor: NormalizedColor;
    outlineColor: NormalizedColor;
  }
): void {
  gl.useProgram(program.program);
  gl.uniformMatrix4fv(program.uniforms.matrix, false, matrix);
  gl.uniform1f(program.uniforms.size, options.pointSize);
  gl.uniform1f(program.uniforms.opacity, options.opacity);
  gl.uniform1f(program.uniforms.innerRadius, PLAYHEAD_INNER_RADIUS_RATIO);
  gl.uniform1f(program.uniforms.fillRadius, PLAYHEAD_FILL_RADIUS_RATIO);
  gl.uniform1f(program.uniforms.borderRadius, PLAYHEAD_BORDER_RADIUS_RATIO);
  gl.uniform1f(program.uniforms.edgeSoftness, PLAYHEAD_EDGE_SOFTNESS_RATIO);
  gl.uniform4fv(program.uniforms.innerColor, options.innerColor);
  gl.uniform4fv(program.uniforms.fillColor, options.fillColor);
  gl.uniform4fv(program.uniforms.borderColor, options.borderColor);
  gl.uniform4fv(program.uniforms.outlineColor, options.outlineColor);
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  if (program.attributes.pos >= 0) {
    gl.enableVertexAttribArray(program.attributes.pos);
    gl.vertexAttribPointer(program.attributes.pos, 3, gl.FLOAT, false, 3 * BYTES_PER_FLOAT, 0);
  }
  gl.drawArrays(gl.POINTS, 0, 1);
}

function createProgram(gl: WebGLRenderingContext | WebGL2RenderingContext): ReplayProgram {
  const vertexShader = compileShader(
    gl,
    gl.VERTEX_SHADER,
    `
attribute vec3 a_pos;
attribute vec3 a_other;
attribute float a_side;
attribute vec4 a_color;
uniform mat4 u_matrix;
uniform vec2 u_viewport;
uniform float u_half_width;
varying vec4 v_color;

void main() {
  vec4 current = u_matrix * vec4(a_pos, 1.0);
  vec4 other = u_matrix * vec4(a_other, 1.0);
  vec2 direction = ((other.xy / other.w) - (current.xy / current.w)) * u_viewport;
  float lengthPx = length(direction);
  if (lengthPx > 0.0001) {
    vec2 normal = vec2(-direction.y, direction.x) / lengthPx;
    current.xy += normal * a_side * u_half_width * 2.0 / u_viewport * current.w;
  }
  gl_Position = current;
  v_color = a_color;
}
`
  );
  const fragmentShader = compileShader(
    gl,
    gl.FRAGMENT_SHADER,
    `
precision mediump float;
uniform float u_opacity;
varying vec4 v_color;

void main() {
  gl_FragColor = vec4(v_color.rgb, v_color.a * u_opacity);
}
`
  );

  const program = gl.createProgram();
  if (!program) throw new Error('Unable to create track replay WebGL program');
  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const message = gl.getProgramInfoLog(program) ?? 'unknown program link error';
    gl.deleteProgram(program);
    throw new Error(`Unable to link track replay WebGL program: ${message}`);
  }

  return {
    program,
    attributes: {
      pos: gl.getAttribLocation(program, 'a_pos'),
      other: gl.getAttribLocation(program, 'a_other'),
      side: gl.getAttribLocation(program, 'a_side'),
      color: gl.getAttribLocation(program, 'a_color'),
    },
    uniforms: {
      matrix: gl.getUniformLocation(program, 'u_matrix'),
      viewport: gl.getUniformLocation(program, 'u_viewport'),
      halfWidth: gl.getUniformLocation(program, 'u_half_width'),
      opacity: gl.getUniformLocation(program, 'u_opacity'),
    },
  };
}

function compileShader(gl: WebGLRenderingContext | WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type);
  if (!shader) throw new Error('Unable to create track replay WebGL shader');
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const message = gl.getShaderInfoLog(shader) ?? 'unknown shader compile error';
    gl.deleteShader(shader);
    throw new Error(`Unable to compile track replay WebGL shader: ${message}`);
  }
  return shader;
}

function createPointProgram(gl: WebGLRenderingContext | WebGL2RenderingContext): ReplayPointProgram {
  const vertexShader = compileShader(
    gl,
    gl.VERTEX_SHADER,
    `
attribute vec3 a_pos;
uniform mat4 u_matrix;
uniform float u_size;

void main() {
  gl_Position = u_matrix * vec4(a_pos, 1.0);
  gl_PointSize = u_size;
}
`
  );
  const fragmentShader = compileShader(
    gl,
    gl.FRAGMENT_SHADER,
    `
precision mediump float;
uniform float u_opacity;
uniform float u_inner_radius;
uniform float u_fill_radius;
uniform float u_border_radius;
uniform float u_edge_softness;
uniform vec4 u_inner_color;
uniform vec4 u_fill_color;
uniform vec4 u_border_color;
uniform vec4 u_outline_color;

void main() {
  float distance_from_center = distance(gl_PointCoord, vec2(0.5));
  float edge_alpha = 1.0 - smoothstep(0.5 - u_edge_softness, 0.5, distance_from_center);
  if (edge_alpha <= 0.0) {
    discard;
  }

  vec4 color = u_outline_color;
  if (distance_from_center < u_inner_radius) {
    color = u_inner_color;
  } else if (distance_from_center < u_fill_radius) {
    color = u_fill_color;
  } else if (distance_from_center < u_border_radius) {
    color = u_border_color;
  }

  gl_FragColor = vec4(color.rgb, color.a * u_opacity * edge_alpha);
}
`
  );

  const program = gl.createProgram();
  if (!program) throw new Error('Unable to create track replay playhead WebGL program');
  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const message = gl.getProgramInfoLog(program) ?? 'unknown program link error';
    gl.deleteProgram(program);
    throw new Error(`Unable to link track replay playhead WebGL program: ${message}`);
  }

  return {
    program,
    attributes: {
      pos: gl.getAttribLocation(program, 'a_pos'),
    },
    uniforms: {
      matrix: gl.getUniformLocation(program, 'u_matrix'),
      size: gl.getUniformLocation(program, 'u_size'),
      opacity: gl.getUniformLocation(program, 'u_opacity'),
      innerRadius: gl.getUniformLocation(program, 'u_inner_radius'),
      fillRadius: gl.getUniformLocation(program, 'u_fill_radius'),
      borderRadius: gl.getUniformLocation(program, 'u_border_radius'),
      edgeSoftness: gl.getUniformLocation(program, 'u_edge_softness'),
      innerColor: gl.getUniformLocation(program, 'u_inner_color'),
      fillColor: gl.getUniformLocation(program, 'u_fill_color'),
      borderColor: gl.getUniformLocation(program, 'u_border_color'),
      outlineColor: gl.getUniformLocation(program, 'u_outline_color'),
    },
  };
}
