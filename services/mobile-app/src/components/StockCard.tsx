import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { StockRanking } from '../websocket/useStockWebSocket';

interface Props {
    ranking: StockRanking;
    rank:    number;
}

const SENTIMENT_COLORS = {
    high:   '#00ff88',
    mid:    '#ffcc00',
    low:    '#ff4757',
};

function scoreColor(score: number) {
    if (score > 0.65) return SENTIMENT_COLORS.high;
    if (score > 0.40) return SENTIMENT_COLORS.mid;
    return SENTIMENT_COLORS.low;
}

export function StockCard({ ranking, rank }: Props) {
    const router = useRouter();
    const sentColor = scoreColor(ranking.compositeScore);

    return (
        <TouchableOpacity
            style={styles.card}
            onPress={() => router.push(`/stock/${ranking.ticker}`)}
            activeOpacity={0.85}
        >
            <View style={styles.left}>
                <Text style={styles.rank}>#{rank}</Text>
                <View>
                    <Text style={styles.ticker}>{ranking.ticker}</Text>
                    <Text style={styles.company} numberOfLines={1}>{ranking.companyName}</Text>
                </View>
            </View>

            <View style={styles.right}>
                <View style={[styles.scoreBadge, { borderColor: sentColor }]}>
                    <Text style={[styles.scoreText, { color: sentColor }]}>
                        {(ranking.compositeScore * 100).toFixed(0)}
                    </Text>
                </View>
                <Text style={styles.pe}>P/E {ranking.peRatio.toFixed(1)}</Text>
                <Text style={styles.mentions}>{ranking.mentionCount} mentions</Text>
            </View>

            {/* Sentiment bar */}
            <View style={styles.barContainer}>
                <View style={[styles.bar, {
                    width: `${ranking.compositeScore * 100}%`,
                    backgroundColor: sentColor
                }]} />
            </View>
        </TouchableOpacity>
    );
}

const styles = StyleSheet.create({
    card: {
        backgroundColor: 'rgba(255,255,255,0.07)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 10,
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.1)',
        overflow: 'hidden',
    },
    left:       { flexDirection: 'row', alignItems: 'center', flex: 1 },
    right:      { alignItems: 'flex-end' },
    rank:       { color: 'rgba(255,255,255,0.4)', fontSize: 13, marginRight: 10, fontWeight: '700' },
    ticker:     { color: '#fff', fontSize: 18, fontWeight: '800' },
    company:    { color: 'rgba(255,255,255,0.6)', fontSize: 12, maxWidth: 160 },
    scoreBadge: { borderWidth: 2, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 2 },
    scoreText:  { fontSize: 20, fontWeight: '900' },
    pe:         { color: 'rgba(255,255,255,0.7)', fontSize: 12, marginTop: 4 },
    mentions:   { color: 'rgba(255,255,255,0.5)', fontSize: 11 },
    barContainer:{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 3,
        backgroundColor: 'rgba(255,255,255,0.05)' },
    bar:         { height: 3, borderRadius: 2 },
});