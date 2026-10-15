import { beforeEach, describe, expect, it, vi } from 'vitest';
import router from '@/router';
import { isAuthenticated } from '@/utils/auth';

vi.mock('@/utils/auth', () => ({ isAuthenticated: vi.fn(() => true) }));
vi.mock('@/views/HomeView.vue', () => ({ default: { template: '<main>Map</main>' } }));
vi.mock('@/views/LoginView.vue', () => ({ default: { template: '<main>Sign in</main>' } }));

describe('unknown route recovery', () => {
  beforeEach(async () => {
    vi.mocked(isAuthenticated).mockReturnValue(true);
    await router.replace('/');
  });

  it.each(['/planner', '/missing/nested/page', '/track/not-a-number'])(
    'returns signed-in users to the map from %s',
    async (path) => {
      await router.push(path);

      expect(router.currentRoute.value.name).toBe('home');
      expect(router.currentRoute.value.path).toBe('/');
    }
  );

  it('requires sign-in when recovering an unknown route without a session', async () => {
    vi.mocked(isAuthenticated).mockReturnValue(false);
    await router.replace('/login');

    await router.push('/planner');

    expect(router.currentRoute.value.name).toBe('login');
  });

  it('keeps the supported planner routes available', async () => {
    await router.push('/plan');
    expect(router.currentRoute.value.name).toBe('planner');

    await router.push('/plan/123');
    expect(router.currentRoute.value.name).toBe('planner');
    expect(router.currentRoute.value.params.id).toBe('123');
  });
});
