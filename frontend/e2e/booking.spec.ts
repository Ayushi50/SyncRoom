import { expect, test } from '@playwright/test';

// Needs the whole stack running: backend on 8080, frontend on 5173 and the mail sink (or Mailpit) on 8025.
test('register, search, book, see it listed and receive the email', async ({ page, request }) => {
  const email = `e2e-${Date.now()}@example.com`;
  await request.delete('http://localhost:8025/api/v1/messages');

  // Landing call to action goes through sign in and comes back to the finder.
  await page.goto('/');
  await page.getByRole('link', { name: 'Find a room' }).first().click();
  await expect(page).toHaveURL(/\/login\?next=%2Frooms%2Ffind/);
  await page.getByRole('link', { name: 'Create an account' }).click();
  await page.getByLabel('Full name').fill('Eve Tester');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('correct-horse-battery');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Find a room' })).toBeVisible();

  // Search for a random weekday so reruns do not collide for 5 people. A weekday is needed, so move to Monday if it is a weekend.
  const d = new Date(Date.now() + (2 + Math.floor(Math.random() * 55)) * 86_400_000);
  while ([0, 6].includes(d.getDay())) d.setDate(d.getDate() + 1);
  const date = d.toISOString().slice(0, 10);
  await page.getByLabel('Date').fill(date);
  await page.getByLabel('From').fill('14:00');
  await page.getByLabel('To').fill('15:00');
  await page.getByLabel('People').fill('5');
  await page.getByRole('button', { name: 'Search' }).click();

  const cards = page.getByRole('article');
  await expect(cards.first()).toBeVisible();
  await expect(cards.first().getByText('Best fit')).toBeVisible();
  // 5 people: Maple (4 seats) must not appear, Birch (6) is the best fit.
  await expect(page.getByRole('article', { name: 'Maple' })).toHaveCount(0);
  const best = (await cards.first().getByRole('heading').textContent()) ?? '';
  expect(['Birch', 'Cedar', 'Pinehurst', 'Redwood']).toContain(best);

  await page.getByRole('button', { name: `Select ${best}` }).click();
  await expect(page.getByRole('complementary', { name: 'Your booking' })).toBeVisible();
  await page.getByLabel('Meeting title').fill('Backend design review');
  await page.getByLabel('Invite people').fill('priya@example.com');
  await page.getByRole('button', { name: 'Confirm booking' }).click();

  await expect(page.getByText('Booked. A confirmation email')).toBeVisible();
  await page.getByRole('link', { name: 'My bookings' }).click();
  await expect(page.getByRole('link', { name: 'Backend design review' })).toBeVisible();

  // Both the organizer and the invitee get an email.
  await expect
    .poll(
      async () => {
        const res = await request.get('http://localhost:8025/api/v1/messages');
        const body = await res.json();
        return body.messages.filter((m: { Subject: string }) =>
          m.Subject.startsWith('Booked: Backend design review'),
        ).length;
      },
      { timeout: 30_000 },
    )
    .toBe(2);
});
