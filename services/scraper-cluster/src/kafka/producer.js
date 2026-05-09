import { Kafka, CompressionTypes, logLevel } from 'kafkajs';
import logger from '../utils/logger.js';

export async function createProducer() {
    const kafka = new Kafka({
        clientId: `scraper-${process.env.SCRAPER || 'generic'}-${process.pid}`,
        brokers: (process.env.KAFKA_BROKERS || 'localhost:9092').split(','),
        logLevel: logLevel.WARN,
        retry: {
            initialRetryTime: 300,
            retries: 10,
            factor: 2,
        },
    });

    const producer = kafka.producer({
        idempotent: true,
        transactionTimeout: 30000,
        compression: CompressionTypes.LZ4,
        maxInFlightRequests: 5,
    });

    await producer.connect();
    logger.info('Kafka producer connected');

    return {
        async sendBatch(topic, messages) {
            const kafkaMsgs = messages.map(m => ({
                key: m.ticker,
                value: JSON.stringify(m),
                headers: {
                    source: m.source,
                    timestamp: new Date().toISOString(),
                },
            }));

            await producer.sendBatch({
                topicMessages: [{
                    topic,
                    messages: kafkaMsgs,
                }],
                compression: CompressionTypes.LZ4,
            });
        },

        async disconnect() {
            await producer.disconnect();
        },
    };
}