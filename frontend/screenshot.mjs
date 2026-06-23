import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });

const viewports = [
  { name: 'mobile-375', width: 375, height: 812 },
  { name: 'mobile-480', width: 480, height: 800 },
  { name: 'tablet-720', width: 720, height: 1024 },
  { name: 'desktop-1280', width: 1280, height: 800 },
];

const port = 4176;
const pages = ['/schools', '/'];

for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();

  for (const p of pages) {
    try {
      await page.goto(`http://localhost:${port}${p}`, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(1500);
      const label = p === '/' ? 'home' : 'schools';
      const filename = `/tmp/shot-${vp.name}-${label}.png`;
      await page.screenshot({ path: filename, fullPage: false });
      console.log(`Saved ${filename}`);
    } catch (e) {
      console.log(`Failed ${vp.name} ${p}: ${e.message}`);
    }
  }
  await context.close();
}

await browser.close();
