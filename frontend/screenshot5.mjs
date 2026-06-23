import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const port = 4176;

// Capture full page screenshots for all key views at mobile width
const views = [
  { path: '/schools', name: 'schools' },
  { path: '/about', name: 'about' },
  { path: '/calendar', name: 'calendar' },
  { path: '/profile', name: 'profile' },
  { path: '/settings', name: 'settings' },
  { path: '/auth?intent=login', name: 'auth' },
];

for (const v of views) {
  const ctx = await browser.newContext({ viewport: { width: 375, height: 812 } });
  const page = await ctx.newPage();
  try {
    await page.goto(`http://localhost:${port}${v.path}`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
    
    // Check for horizontal overflow
    const overflow = await page.evaluate(() => document.body.scrollWidth > document.body.clientWidth);
    
    await page.screenshot({ path: `/tmp/mobile-375-${v.name}.png`, fullPage: true });
    console.log(`${v.name}: overflow=${overflow}, screenshot saved`);
  } catch (e) {
    console.log(`${v.name} failed: ${e.message}`);
  }
  await ctx.close();
}

// Also check desktop for comparison
const ctxD = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const pageD = await ctxD.newPage();
for (const v of views) {
  try {
    await pageD.goto(`http://localhost:${port}${v.path}`, { waitUntil: 'networkidle', timeout: 15000 });
    await pageD.waitForTimeout(1000);
    const overflow = await pageD.evaluate(() => document.body.scrollWidth > document.body.clientWidth);
    console.log(`DESKTOP ${v.name}: overflow=${overflow}`);
  } catch (e) {
    console.log(`DESKTOP ${v.name} failed: ${e.message}`);
  }
}
await ctxD.close();

await browser.close();
