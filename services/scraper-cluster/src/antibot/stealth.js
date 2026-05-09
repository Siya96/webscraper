import { chromium } from 'playwright-extra';
import StealthPlugin from 'puppeteer-extra-plugin-stealth';
import { FingerprintGenerator } from 'fingerprint-generator';
import UserAgent from 'user-agents';

chromium.use(StealthPlugin());

const fpGenerator = new FingerprintGenerator({
    browsers: ['chrome'],
    operatingSystems: ['windows', 'macos', 'linux'],
    locales: ['en-US', 'en-GB'],
});

export class StealthBrowser {
    constructor(proxyRotator) {
        this.proxyRotator = proxyRotator;
        this.browser = null;
        this.sessionCount = 0;
        this.MAX_SESSIONS_PER_BROWSER = 30;
    }

    async getBrowser() {
        if (!this.browser || this.sessionCount >= this.MAX_SESSIONS_PER_BROWSER) {
            if (this.browser) await this.browser.close();
            const proxy = await this.proxyRotator.getNext();
            const fp = fpGenerator.getFingerprint();

            this.browser = await chromium.launch({
                headless: true,
                args: [
                    '--no-sandbox',
                    '--disable-setuid-sandbox',
                    '--disable-blink-features=AutomationControlled',
                    '--disable-infobars',
                    '--disable-extensions',
                    `--window-size=${fp.screen.width},${fp.screen.height}`,
                    '--disable-web-security',
                    '--disable-features=IsolateOrigins,site-per-process',
                ],
                proxy: proxy ? {
                    server: `${proxy.protocol}://${proxy.host}:${proxy.port}`,
                    username: proxy.username,
                    password: proxy.password,
                } : undefined,
            });
            this.sessionCount = 0;
        }
        return this.browser;
    }

    async newPage() {
        const browser = await this.getBrowser();
        const fp = fpGenerator.getFingerprint();
        const ua = new UserAgent({ deviceCategory: 'desktop' }).toString();

        const ctx = await browser.newContext({
            userAgent: ua,
            viewport: { width: fp.screen.width, height: fp.screen.height },
            locale: fp.navigator.language,
            timezoneId: this.randomTimezone(),
            extraHTTPHeaders: {
                'Accept-Language': fp.navigator.language + ',en;q=0.9',
                'Accept-Encoding': 'gzip, deflate, br',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'DNT': '1',
                'Upgrade-Insecure-Requests': '1',
            },
        });

        const page = await ctx.newPage();

        // Override navigator properties to evade detection
        await page.addInitScript((fp) => {
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            Object.defineProperty(navigator, 'languages', { get: () => fp.languages });
            Object.defineProperty(navigator, 'plugins', { get: () => fp.plugins });
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => fp.hardwareConcurrency });
            Object.defineProperty(screen, 'width',  { get: () => fp.screen.width  });
            Object.defineProperty(screen, 'height', { get: () => fp.screen.height });
        }, fp);

        // Human-like mouse movements
        await page.mouse.move(
            Math.random() * fp.screen.width,
            Math.random() * fp.screen.height
        );

        this.sessionCount++;
        return { page, ctx };
    }

    async humanDelay(min = 500, max = 2000) {
        const ms = Math.floor(Math.random() * (max - min) + min);
        await new Promise(r => setTimeout(r, ms));
    }

    async humanScroll(page) {
        await page.evaluate(async () => {
            const totalHeight = document.body.scrollHeight;
            let scrolled = 0;
            while (scrolled < totalHeight * 0.7) {
                const step = Math.floor(Math.random() * 200 + 100);
                window.scrollBy(0, step);
                scrolled += step;
                await new Promise(r => setTimeout(r, Math.random() * 300 + 100));
            }
        });
    }

    randomTimezone() {
        const zones = ['America/New_York', 'America/Chicago', 'America/Los_Angeles',
            'Europe/London', 'Europe/Berlin', 'Asia/Tokyo'];
        return zones[Math.floor(Math.random() * zones.length)];
    }
}