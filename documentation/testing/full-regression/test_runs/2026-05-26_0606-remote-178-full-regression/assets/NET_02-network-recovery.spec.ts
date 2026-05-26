import { test, expect } from '@playwright/test';

test.use({ channel: 'chrome' });

test('MTL Explorer startup API failure shows retry state', async ({ page }) => {
  const baseUrl = 'http://178.105.173.254:18080/mtl/';
  const failedRequests: string[] = [];

  await page.goto(`${baseUrl}login`);
  await page.getByPlaceholder('Username').fill('mtl');
  await page.getByPlaceholder('Password').fill('change-me');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(baseUrl, { timeout: 30000 });
  await page.getByText(/Tracks/).first().waitFor({ timeout: 30000 });

  await page.evaluate(async () => {
    await new Promise<void>((resolve) => {
      const request = indexedDB.deleteDatabase('mtl_db');
      request.onsuccess = () => resolve();
      request.onerror = () => resolve();
      request.onblocked = () => resolve();
    });
    if ('caches' in window) {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
    }
  });

  await page.route('**/mtl/api/**', async (route) => {
    const request = route.request();
    failedRequests.push(`${request.method()} ${new URL(request.url()).pathname}${new URL(request.url()).search}`);
    await route.abort('failed');
  });

  await page.goto(baseUrl);
  await expect(page.getByText('Unable to load tracks')).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible({ timeout: 10000 });

  const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  console.log(JSON.stringify({
    failedRequests,
    retryPresent: await page.getByRole('button', { name: 'Retry' }).isVisible(),
    frozenSplash: text.includes('LOADING YOUR TRAILS') && !text.includes('Unable to load tracks'),
    visibleText: text.slice(0, 500),
  }));
});
