// Takes screenshots of the landing page and the finder at 1280 and 390 px wide, and checks for horizontal overflow.
import { chromium } from '@playwright/test';

const base = process.env.BASE_URL ?? 'http://localhost:5173';
const out = new URL('../../docs/screenshots/', import.meta.url).pathname;
const browser = await chromium.launch();
let failed = false;

for (const width of [1280, 390]) {
  const ctx = await browser.newContext({ viewport: { width, height: width === 1280 ? 900 : 844 } });
  const page = await ctx.newPage();
  const overflow = async (name) => {
    const w = await page.evaluate(() => document.documentElement.scrollWidth);
    if (w > width) {
      console.log(`OVERFLOW ${name} at ${width}: scrollWidth ${w}`);
      failed = true;
    } else console.log(`ok ${name} at ${width}`);
  };

  await page.goto(base + '/');
  await page.waitForLoadState('load');
  await overflow('landing');
  await page.screenshot({ path: `${out}landing-${width}.png`, fullPage: true });

  for (const p of ['/login', '/register', '/does-not-exist']) {
    await page.goto(base + p);
    await overflow(p);
  }

  await page.goto(base + '/register');
  await page.getByLabel('Full name').fill('Shot Taker');
  await page.getByLabel('Email').fill(`shots-${width}-${Date.now()}@example.com`);
  await page.getByLabel('Password').fill('correct-horse-battery');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL('**/rooms/find');

  const d = new Date(Date.now() + 86_400_000);
  while ([0, 6].includes(d.getDay())) d.setDate(d.getDate() + 1);
  await page.getByLabel('Date').fill(d.toISOString().slice(0, 10));
  await page.getByLabel('From').fill('10:00');
  await page.getByLabel('To').fill('11:00');
  await page.getByLabel('People').fill('6');
  await page.getByRole('button', { name: 'Video call' }).click();
  await page.getByRole('button', { name: 'Search' }).click();
  await page.getByRole('article').first().waitFor();
  await page
    .getByRole('button', { name: /Select/ })
    .first()
    .click();
  await page.getByRole('complementary', { name: 'Your booking' }).waitFor();
  await overflow('finder');
  await page.screenshot({ path: `${out}finder-${width}.png`, fullPage: true });

  for (const p of ['/bookings']) {
    await page.goto(base + p);
    await page.waitForLoadState('load');
    await overflow(p);
  }
  await ctx.close();
}
await browser.close();
process.exit(failed ? 1 : 0);
