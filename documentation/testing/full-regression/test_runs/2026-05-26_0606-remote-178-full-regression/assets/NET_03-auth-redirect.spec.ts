import { test, expect } from '@playwright/test';

test.use({ channel: 'chrome' });

test('MTL Explorer invalid auth redirects to login', async ({ page }) => {
  const baseUrl = 'http://178.105.173.254:18080/mtl/';

  await page.goto(`${baseUrl}login`);
  await page.getByPlaceholder('Username').fill('mtl');
  await page.getByPlaceholder('Password').fill('change-me');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(baseUrl, { timeout: 30000 });
  await page.getByText(/Tracks/).first().waitFor({ timeout: 30000 });

  await page.evaluate(() => {
    localStorage.setItem('mtl.jwt', 'invalid-token-for-net-03');
  });

  await page.reload();
  await expect(page).toHaveURL(/\/mtl\/login/, { timeout: 30000 });
  await expect(page.getByPlaceholder('Username')).toBeVisible({ timeout: 10000 });

  const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  console.log(JSON.stringify({
    url: page.url(),
    loginVisible: await page.getByPlaceholder('Username').isVisible(),
    visibleText: text.slice(0, 500),
  }));
});
