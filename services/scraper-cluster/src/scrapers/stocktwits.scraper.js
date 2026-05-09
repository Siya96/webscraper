import { StealthBrowser } from '../antibot/stealth.js';
import { ProxyRotator }   from '../antibot/proxy-rotator.js';
import { createHash }     from 'crypto';
import axios               from 'axios';
import logger              from '../utils/logger.js';

export class StockTwitsScraper {
    constructor(kafkaProducer) {
        this.producer     = kafkaProducer;
        this.proxyRotator = new ProxyRotator();
        this.browser      = new StealthBrowser(this.proxyRotator);
        this.running      = false;
    }

    async start() {
        this.running = true;

        while (this.running) {
            await this.scrapeStreaming();
            await new Promise(r => setTimeout(r, 30_000)); // 30s interval
        }
    }

    async scrapeStreaming() {
        const { page, ctx } = await this.browser.newPage();
        try {
            // Use StockTwits public API
            await page.goto('https://api.stocktwits.com/api/2/streams/trending.json', {
                waitUntil: 'domcontentloaded',
                timeout: 15_000,
            });

            const json = await page.evaluate(() => {
                try { return JSON.parse(document.body.innerText); }
                catch { return null; }
            });

            if (!json?.messages) return;

            const mentions = json.messages
                .filter(m => m.symbols?.length > 0)
                .flatMap(m =>
                    m.symbols.map(sym => ({
                        id:             createHash('md5').update(String(m.id)).digest('hex'),
                        ticker:         sym.symbol,
                        source:         'stocktwits',
                        rawText:        m.body,
                        contentHash:    createHash('sha256').update(m.body + m.id).digest('hex'),
                        author:         m.user.username,
                        authorFollowers: m.user.followers_count || 0,
                        upvotes:        m.likes?.total || 0,
                        timestamp:      m.created_at,
                        url:            `https://stocktwits.com/${m.user.username}/message/${m.id}`,
                        // StockTwits provides basic sentiment
                        sentimentHint:  m.entities?.sentiment?.basic,
                    }))
                );

            if (mentions.length > 0) {
                await this.producer.sendBatch('raw-mentions', mentions);
                logger.info(`StockTwits: ${mentions.length} mentions`);
            }
        } finally {
            await ctx.close();
        }
    }
}