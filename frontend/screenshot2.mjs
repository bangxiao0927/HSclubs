import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const port = 4176;

// Mobile 375 - schools page
const ctx1 = await browser.newContext({ viewport: { width: 375, height: 812 } });
const page1 = await ctx1.newPage();
await page1.goto(`http://localhost:${port}/schools`, { waitUntil: 'networkidle', timeout: 15000 });
await page1.waitForTimeout(1500);
await page1.screenshot({ path: '/tmp/m375-schools.png', fullPage: false });

// Mobile 375 - home page
await page1.goto(`http://localhost:${port}/`, { waitUntil: 'networkidle', timeout: 15000 });
await page1.waitForTimeout(1500);
await page1.screenshot({ path: '/tmp/m375-home.png', fullPage: false });

// Check if hamburger menu is visible
const hamburger = await page1.$('.mobile-menu-toggle');
console.log('Hamburger visible:', hamburger !== null);
if (hamburger) {
  await hamburger.click();
  await page1.waitForTimeout(800);
  await page1.screenshot({ path: '/tmp/m375-menu-open.png', fullPage: false });
  console.log('Menu opened, screenshot saved');
}

await ctx1.close();

// Desktop 1280 - schools
const ctx2 = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page2 = await ctx2.newPage();
await page2.goto(`http://localhost:${port}/schools`, { waitUntil: 'networkidle', timeout: 15000 });
await page2.waitForTimeout(1500);
await page2.screenshot({ path: '/tmp/d1280-schools.png', fullPage: false });

await browser.close();
console.log('All screenshots done');
