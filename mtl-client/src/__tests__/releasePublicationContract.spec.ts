import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Configuration, ReleaseUpdateResponseFromJSON } from 'x8ing-mtl-api-typescript-fetch';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';

vi.mock('@/utils/auth', () => ({ isAuthenticated: () => true, getAuthenticatedUsername: () => 'contract-user' }));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: vi.fn() }));
import { getApiConfiguration } from '@/utils/openApiClient';

// The private verification command supplies responses from the actual Java controller.
const directory = process.env.MTL_RELEASE_CONTRACT_DIRECTORY;
const snapshots = directory ? JSON.parse(readFileSync(join(directory, 'api-snapshots.json'), 'utf8')) : {};

describe.skipIf(!directory)('published release API contract', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it.each([
    ['componentAvailable', 'componentInstalled'],
    ['appAvailable', 'appInstalled'],
  ])('consumes %s through the generated API and completes after %s', async (available, installed) => {
    const responses = [snapshots[available], snapshots[installed]];
    const fetchApi = vi.fn(
      async () =>
        new Response(JSON.stringify(responses.shift()), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
    );
    vi.mocked(getApiConfiguration).mockReturnValue(
      new Configuration({ basePath: 'https://example.com/mtl', fetchApi })
    );
    const store = useReleaseUpdatesStore();
    await store.refresh(true);
    expect(store.requestFailed).toBe(false);
    expect(store.hasUpdate).toBe(true);
    expect(store.noticeVisible).toBe(true);
    store.dismissNotice();
    expect(store.hasUpdate).toBe(true);
    await store.refresh(true);
    expect(store.hasUpdate).toBe(false);
    expect(store.installedNoticeVisible).toBe(true);
    expect(store.status?.checkedAt).toBeInstanceOf(Date);
    expect(Number.isNaN(store.status?.checkedAt?.getTime())).toBe(false);
    expect(fetchApi.mock.calls).toHaveLength(2);

    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    reopened.applyStatus(ReleaseUpdateResponseFromJSON(snapshots[installed]));
    expect(reopened.installedNoticeVisible).toBe(true);
    reopened.openDetails();
    expect(reopened.installedNoticeVisible).toBe(false);
    expect(reopened.detailsVisible).toBe(true);
  });
});
