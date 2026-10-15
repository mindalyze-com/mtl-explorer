import { describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ getChartSeries: vi.fn().mockResolvedValue({ buckets: [] }) }));
vi.mock('x8ing-mtl-api-typescript-fetch', () => ({
  ChartSeriesControllerApi: class {
    getChartSeries = mocks.getChartSeries;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
import { fetchChartSeries } from '@/utils/chartSeriesAdapter';

describe('Chart series freshness', () => {
  it('revalidates even an existing fresh HTTP cache entry', async () => {
    await fetchChartSeries(42);
    expect(mocks.getChartSeries).toHaveBeenCalledWith(expect.objectContaining({ trackId: 42 }), { cache: 'no-cache' });
  });
});
