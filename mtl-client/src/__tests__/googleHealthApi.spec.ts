import { afterEach, describe, expect, it, vi } from 'vitest';
import { ResponseError, type GHRecordValue } from 'x8ing-mtl-api-typescript-fetch';
import { googleHealthApi } from '@/features/health/api/googleHealthApi';

describe('googleHealthApi', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('decodes the generated record measurement contract and preserves numeric zero', async () => {
    const measurement: GHRecordValue = { value: 0, unit: 'steps' };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            id: 'point:1',
            dataType: 'steps',
            representation: 'DAILY_ROLLUP',
            measurement,
            valueSummary: '0 steps',
            payload: { steps: { countSum: '0' }, providerExtension: true },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    const record = await googleHealthApi.record('point:1');
    expect(record.measurement).toEqual(measurement);
    expect(record.valueSummary).toBe('0 steps');
    expect(record.payload).toEqual({ steps: { countSum: '0' }, providerExtension: true });
  });

  it('sends the reauthorization intent as an OAuth start query parameter', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          authorizationUrl: 'https://accounts.google.test/authorize',
          oauthMode: 'MANUAL',
          intent: 'REAUTHORIZE',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);

    await googleHealthApi.connect(true);

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/google-health/oauth/start?reauthorize=true');
  });

  it('uses the generated grouped endpoint without converting civil dates to timestamps', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          startDate: '2026-03-29',
          endDate: '2026-03-30',
          requestedGrouping: 'WEEK',
          resolvedGrouping: 'WEEK',
          series: [],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);
    const result = await googleHealthApi.groupedMetrics('2026-03-29', '2026-03-30', 'WEEK', 'Pacific/Kiritimati');
    const query = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost').searchParams;
    expect(query.get('startDate')).toBe('2026-03-29');
    expect(query.get('endDate')).toBe('2026-03-30');
    expect(query.get('timeZone')).toBe('Pacific/Kiritimati');
    expect(query.get('groupBy')).toBe('WEEK');
    expect(result.startDate).toBe('2026-03-29');
  });

  it('uses the grouped endpoint for second averages and preserves the range and time', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          startDate: '2026-10-14',
          endDate: '2026-10-15',
          requestedGrouping: 'SECOND',
          resolvedGrouping: 'SECOND',
          series: [
            {
              dataType: 'heart-rate',
              grouping: 'SECOND',
              detailedPoints: [{ time: '2026-10-14T12:00:12Z', value: 70, minimum: 60, maximum: 80 }],
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);
    const result = await googleHealthApi.groupedMetrics('2026-10-14', '2026-10-15', 'SECOND', 'Europe/Zurich');
    const url = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost');
    expect(url.pathname).toContain('/api/google-health/metrics/grouped');
    expect(url.searchParams.get('groupBy')).toBe('SECOND');
    expect(result.resolvedGrouping).toBe('SECOND');
    expect(result.series![0]!.detailedPoints![0]).toEqual({
      time: new Date('2026-10-14T12:00:12Z'),
      value: 70,
      minimum: 60,
      maximum: 80,
    });
  });

  it('surfaces safe problem details returned by the server', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            type: 'about:blank',
            title: 'Conflict',
            status: 409,
            detail: 'Disconnect Google Health before deleting the local mirror',
          }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } }
        )
      )
    );

    const failure = await googleHealthApi.deleteMirror('DELETE').catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(Error);
    expect((failure as Error).message).toBe('Disconnect Google Health before deleting the local mirror');
    const original = (failure as Error).cause;
    expect(original).toBeInstanceOf(ResponseError);
    expect((original as ResponseError).response.status).toBe(409);
    expect((original as ResponseError).response.bodyUsed).toBe(false);
  });

  it('passes inclusive calendar record filters and preserves generated temporal fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          records: [
            {
              id: 'point:1',
              temporalKind: 'CALENDAR_DATE',
              civilDate: '2026-08-02',
              recordTime: '2026-08-02T00:00:00Z',
            },
            {
              id: 'point:2',
              temporalKind: 'CIVIL',
              civilTime: '2026-08-02T11:30:00.123456',
              routeState: 'FAILED',
              routeError: 'Route export is unavailable.',
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);
    const response = await googleHealthApi.records({
      startDate: '2026-08-02',
      endDate: '2026-08-02',
      timeZone: 'America/Los_Angeles',
    });
    const query = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost').searchParams;
    expect(query.get('startDate')).toBe('2026-08-02');
    expect(query.get('endDate')).toBe('2026-08-02');
    expect(query.get('timeZone')).toBe('America/Los_Angeles');
    expect(query.has('start')).toBe(false);
    expect(query.has('end')).toBe(false);
    expect(response.records?.[0]?.civilDate).toBe('2026-08-02');
    expect(response.records?.[0]?.recordTime).toBeInstanceOf(Date);
    expect(response.records?.[1]?.civilTime).toBe('2026-08-02T11:30:00.123456');
    expect(response.records?.[1]?.routeError).toBe('Route export is unavailable.');
  });
});
