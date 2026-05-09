import 'dotenv/config';
import { RedditScraper }      from './scrapers/reddit.scraper.js';
import { TwitterScraper }     from './scrapers/twitter.scraper.js';
import { StockTwitsScraper }  from './scrapers/stocktwits.scraper.js';
import { WSBScraper }         from './scrapers/wsb.scraper.js';
import { SeekingAlphaScraper }from './scrapers/seekingalpha.scraper.js';
import { createProducer }     from './kafka/producer.js';
import logger                  from './utils/logger.js';

const SCRAPER_MAP = {
    reddit:       RedditScraper,
    twitter:      TwitterScraper,
    stocktwits:   StockTwitsScraper,
    wsb:          WSBScraper,
    seekingalpha: SeekingAlphaScraper,
};

async function main() {
    const scraperType = process.env.SCRAPER || 'reddit';
    const ScraperClass = SCRAPER_MAP[scraperType];

    if (!ScraperClass) {
        logger.error(`Unknown scraper: ${scraperType}`);
        process.exit(1);
    }

    const producer = await createProducer();
    const scraper  = new ScraperClass(producer);

    logger.info(`Starting ${scraperType} scraper...`);

    // Graceful shutdown
    process.on('SIGTERM', async () => {
        await scraper.stop();
        await producer.disconnect();
        process.exit(0);
    });

    await scraper.start();
}

main().catch(err => {
    logger.error('Fatal error:', err);
    process.exit(1);
});