#!/bin/bash
KAFKA="kafka:29092"

kafka-topics.sh --bootstrap-server $KAFKA --create \
  --topic raw-mentions --partitions 12 --replication-factor 1 \
  --config retention.ms=31536000000 --config compression.type=lz4 \
  --config max.message.bytes=10485760

kafka-topics.sh --bootstrap-server $KAFKA --create \
  --topic scored-mentions --partitions 12 --replication-factor 1 \
  --config retention.ms=31536000000 --config compression.type=lz4

kafka-topics.sh --bootstrap-server $KAFKA --create \
  --topic live-rankings --partitions 6 --replication-factor 1 \
  --config retention.ms=3600000 --config cleanup.policy=compact

kafka-topics.sh --bootstrap-server $KAFKA --create \
  --topic stock-fundamentals --partitions 4 --replication-factor 1

kafka-topics.sh --bootstrap-server $KAFKA --create \
  --topic stock-prices --partitions 4 --replication-factor 1

echo "✅ All Kafka topics created!"
