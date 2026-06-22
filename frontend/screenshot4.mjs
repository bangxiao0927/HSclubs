import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const port = 4176;

async function checkPage(width, label, path) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 } });
  const page = await ctx.newPage();
  await page.goto(`http://localhost:${port}${path}`, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1500);

  const data = await page.evaluate(() => {
    const cs = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const s = window.getComputedStyle(el);
      return {
        display: s.display,
        gridTemplateColumns: s.gridTemplateColumns,
        flexDirection: s.flexDirection,
        padding: s.padding,
        fontSize: s.fontSize,
      };
    };

    // Check for overflow
    const bodyOverflow = document.body.scrollWidth > document.body.clientWidth;
    
    return {
      homeHero: cs('.home-hero'),
      topGrid: cs('.top-grid'),
      clubDirectory: cs('.club-directory'),
      clubRow: cs('.club-row'),
      headerInner: cs('.header-inner'),
      search: cs('.search-bar'),
      bodyHorizontalScroll: bodyOverflow,
      bodyScrollWidth: document.body.scrollWidth,
      bodyClientWidth: document.body.clientWidth,
    };
  });

  console.log(`\n=== ${label} (${path} @ ${width}) ===`);
  console.log(JSON.stringify(data, null, 2));

  await ctx.close();
}

// Check home page at different widths
await checkPage(375, 'Mobile 375', '/');
await checkPage(480, 'Mobile 480', '/');
await checkPage(720, 'Tablet 720', '/');
await checkPage(1280, 'Desktop 1280', '/');

// Check schools page
await checkPage(375, 'Mobile 375', '/schools');
await checkPage(720, 'Tablet 720', '/schools');

await browser.close();
