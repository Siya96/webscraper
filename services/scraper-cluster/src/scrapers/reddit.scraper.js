import { StealthBrowser } from '../antibot/stealth.js';
import { ProxyRotator }   from '../antibot/proxy-rotator.js';
import { createHash }     from 'crypto';
import logger              from '../utils/logger.js';
import PQueue              from 'p-queue';

const SUBREDDITS = [
    'wallstreetbets', 'stocks', 'investing', 'options',
    'pennystocks', 'ValueInvesting', 'SecurityAnalysis',
    'stockmarket', 'thetagang', 'Superstonk'
];

// Known stock tickers regex (common ones + dynamic detection)
const TICKER_REGEX = /\b([A-Z]{1,5})\b/g;
const COMMON_ENGLISH = new Set(['I', 'A', 'CEO', 'IPO', 'ETF', 'EPS', 'DD',
    'PE', 'OP', 'US', 'IT', 'UK', 'EU']);

export class RedditScraper {
    constructor(kafkaProducer) {
        this.producer    = kafkaProducer;
        this.proxyRotator = new ProxyRotator();
        this.browser     = new StealthBrowser(this.proxyRotator);
        this.queue       = new PQueue({ concurrency: 3 });
        this.running     = false;
        this.seenHashes  = new Set();
    }

    async start() {
        this.running = true;

        while (this.running) {
            for (const sub of SUBREDDITS) {
                this.queue.add(() => this.scrapeSub(sub));
            }
            await this.queue.onIdle();
            // Wait 2 minutes between full cycles
            await new Promise(r => setTimeout(r, 120_000));
        }
    }

    async scrapeSub(subreddit) {
        const { page, ctx } = await this.browser.newPage();
        try {
            await page.goto(`https://www.reddit.com/r/${subreddit}/new.json?limit=100`, {
                waitUntil: 'domcontentloaded',
                timeout: 30_000,
            });

            const json = await page.evaluate(() => {
                try { return JSON.parse(document.body.innerText); }
                catch { return null; }
            });

            if (!json?.data?.children) return;

            const posts = json.data.children.map(c => c.data);
            const mentions = [];

            for (const post of posts) {
                const text   = `${post.title} ${post.selftext}`;
                const tickers = this.extractTickers(text);

                for (const ticker of tickers) {
                    const hash = this.hash(`${post.id}:${ticker}`);
                    if (this.seenHashes.has(hash)) continue;
                    this.seenHashes.add(hash);

                    mentions.push({
                        id:             hash,
                        ticker,
                        source:         subreddit === 'wallstreetbets' ? 'wsb' : 'reddit',
                        rawText:        text.substring(0, 1000),
                        contentHash:    hash,
                        author:         post.author,
                        authorFollowers: post.author_karma || 0,
                        upvotes:        post.score,
                        timestamp:      new Date(post.created_utc * 1000).toISOString(),
                        url:            `https://reddit.com${post.permalink}`,
                    });
                }
            }

            if (mentions.length > 0) {
                await this.producer.sendBatch('raw-mentions', mentions);
                logger.info(`r/${subreddit}: ${mentions.length} mentions`);
            }

        } catch (err) {
            logger.error(`Reddit scrape error (${subreddit}):`, err.message);
            this.proxyRotator.reportFailure({});
        } finally {
            await ctx.close();
        }
    }

    extractTickers(text) {
        const found = new Set();
        let match;
        while ((match = TICKER_REGEX.exec(text)) !== null) {
            const word = match[1];
            if (word.length >= 2 && word.length <= 5 && !COMMON_ENGLISH.has(word)) {
                found.add(word);
            }
        }
        return [...found];
    }

    hash(str) {
        return createHash('sha256').update(str).digest('hex').substring(0, 32);
    }

    async stop() {
        this.running = false;
        await this.queue.onIdle();
    }
}