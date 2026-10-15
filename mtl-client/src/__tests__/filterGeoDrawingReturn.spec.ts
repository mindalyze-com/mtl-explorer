import { describe, expect, it, vi } from 'vitest';
import { useGeoDrawing } from '@/components/map/composables/useGeoDrawing';

vi.mock('@/layers/GeoDrawingOverlay', () => ({ GeoDrawingOverlay: vi.fn() }));

function drawingContext() {
  const methods = useGeoDrawing();
  const filterTool = { close: vi.fn(), openCriteria: vi.fn(), onGeoDrawingComplete: vi.fn() };
  const context = {
    ...methods,
    overlayMap: {},
    $refs: { filterTool },
    geoDrawingOverlay: { startDrawing: vi.fn(), cancelDrawing: vi.fn() },
    geoDrawingParamDef: null as unknown,
    geoDrawPointCount: 0,
    renderExistingGeoShapes: vi.fn(),
  };
  return { methods, context, filterTool };
}

describe('returning from filter area drawing', () => {
  it('returns to criteria on cancel and preserves configured geometry', () => {
    const { methods, context, filterTool } = drawingContext();
    methods.onStartGeoDrawing.call(context as never, { name: 'AREA', type: 'GEO_RECTANGLE' });
    expect(filterTool.close).toHaveBeenCalledOnce();
    methods.onGeoDrawCancel.call(context as never);
    expect(context.geoDrawingOverlay.cancelDrawing).toHaveBeenCalledOnce();
    expect(context.renderExistingGeoShapes).toHaveBeenCalledTimes(2);
    expect(filterTool.openCriteria).toHaveBeenCalledOnce();
    expect(filterTool.onGeoDrawingComplete).not.toHaveBeenCalled();
    expect(context.geoDrawingParamDef).toBeNull();
    methods.onGeoDrawCancel.call(context as never);
    expect(filterTool.openCriteria).toHaveBeenCalledOnce();
  });

  it.each([
    { type: 'GEO_CIRCLE' as const, mode: 'circle', shape: { lat: 0, lng: 0, radiusM: 100 } },
    { type: 'GEO_RECTANGLE' as const, mode: 'rectangle', shape: { minLat: 0, minLng: 0, maxLat: 1, maxLng: 1 } },
    {
      type: 'GEO_POLYGON' as const,
      mode: 'polygon',
      shape: {
        coordinates: [
          [0, 0],
          [1, 0],
          [0, 1],
        ],
      },
    },
  ])('stores a completed $mode before reopening criteria', ({ type, mode, shape }) => {
    const { methods, context, filterTool } = drawingContext();
    const parameter = { name: 'AREA', type };
    methods.onStartGeoDrawing.call(context as never, parameter);
    expect(context.geoDrawingOverlay.startDrawing).toHaveBeenCalledWith(
      mode,
      expect.any(Function),
      expect.any(Function)
    );
    const completed = context.geoDrawingOverlay.startDrawing.mock.calls[0][1];
    completed(shape);
    expect(filterTool.onGeoDrawingComplete).toHaveBeenCalledWith(parameter, shape);
    expect(filterTool.openCriteria).toHaveBeenCalledOnce();
    expect(filterTool.onGeoDrawingComplete.mock.invocationCallOrder[0]).toBeLessThan(
      filterTool.openCriteria.mock.invocationCallOrder[0]
    );
    expect(context.geoDrawingParamDef).toBeNull();
  });
});
