import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const port = 4176;

async function checkLayout(width, label) {
  const ctx = await browser.newContext({ viewport: { width, height: 800 } });
  const page = await ctx.newPage();
  await page.goto(`http://localhost:${port}/schools`, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1500);

  const data = await page.evaluate(() => {
    const header = document.querySelector('.header');
    const headerInner = document.querySelector('.header-inner');
    const nav = document.querySelector('.nav');
    const headerRight = document.querySelector('.header-right');
    const search = document.querySelector('.search-bar');
    const hamburger = document.querySelector('.mobile-menu-toggle');
    const pageShell = document.querySelector('.page-shell');
    const schoolGrid = document.querySelector('.school-grid');

    const cs = (el) => el ? window.getComputedStyle(el) : null;
    const info = (el) => {
      if (!el) return null;
      const s = cs(el);
      return {
        display: s.display,
        width: s.width,
        paddingInline: s.paddingLeft,
        gridTemplateColumns: s.gridTemplateColumns,
      };
    };

    return {
      headerPadding: header ? cs(header).paddingTop : null,
      navDisplay: nav ? cs(nav).display : null,
      headerRightDisplay: headerRight ? cs(headerRight).display : null,
      searchDisplay: search ? cs(search).display : null,
      hamburgerDisplay: hamburger ? cs(hamburger).display : null,
      pageShellPadding: pageShell ? cs(pageShell).paddingLeft : null,
      schoolGridCols: schoolGrid ? cs(schoolGrid).gridTemplateColumns : null,
    };
  });

  console.log(`\n=== ${label} (width=${width}) ===`);
  console.log(JSON.stringify(data, null, 2));

  await ctx.close();
}

await checkLayout(375, 'Mobile 375');
await checkLayout(480, 'Mobile 480');
await checkLayout(720, 'Tablet 720');
await checkLayout(860, 'Tablet 860');
await checkLayout(1280, 'Desktop 1280');

await browser.close();
