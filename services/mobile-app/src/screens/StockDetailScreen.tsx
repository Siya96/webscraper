import React, { useEffect, useState, useCallback } from 'react';
import {
    View, Text, ScrollView, StyleSheet,
    ActivityIndicator, RefreshControl, Dimensions
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { LineChart } from 'react-native-chart-kit';
import { useStockStore } from '../store/stockStore';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const API = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080';

interface SentimentPoint {
    bucket: string;
    mentionCount: number;
    weightedSentiment: number;
}

interface StockDetail {
    ticker: string;
    companyName: string;
    sector: string;
    peRatio: number;
    forwardPe: number;
    pbRatio: number;
    marketCap: number;
    price: number;
    change: number;
    changePct: number;
}

export default function StockDetailScreen() {
    const { ticker }   = useLocalSearchParams<{ ticker: string }>();
    const windowSeconds = useStockStore(s => s.windowSeconds);

    const [detail,    setDetail]    = useState<StockDetail | null>(null);
    const [sentiment, setSentiment] = useState<SentimentPoint[]>([]);
    const [loading,   setLoading]   = useState(true);
    const [refreshing,setRefreshing]= useState(false);

    const load = useCallback(async () => {
        try {
            const [detailRes, sentRes] = await Promise.all([
                fetch(`${API}/api/v1/market/quote/${ticker}`),
                fetch(`${API}/api/v1/stocks/${ticker}/sentiment?windowSeconds=${windowSeconds}&bucketSeconds=${Math.floor(windowSeconds / 24)}`),
            ]);
            setDetail(await detailRes.json());
            setSentiment(await sentRes.json());
        } catch (e) {
            console.error('Load error:', e);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, [ticker, windowSeconds]);

    useEffect(() => { load(); }, [load]);

    if (loading) return (
        <View style={styles.center}>
            <ActivityIndicator size="large" color="#00ff88" />
        </View>
    );

    const chartData = {
        labels: sentiment.slice(-12).map(p => {
            const d = new Date(p.bucket);
            return `${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`;
        }),
        datasets: [{
            data: sentiment.slice(-12).map(p =>
                Math.max(-1, Math.min(1, p.weightedSentiment))
            ),
            color: (opacity = 1) => `rgba(0, 255, 136, ${opacity})`,
            strokeWidth: 2,
        }],
    };

    const isPositive = (detail?.changePct ?? 0) >= 0;

    return (
        <LinearGradient colors={['#0f0c29','#302b63','#24243e']} style={{flex:1}}>
            <ScrollView
                contentContainerStyle={styles.scroll}
                refreshControl={
                    <RefreshControl refreshing={refreshing} onRefresh={() => {
                        setRefreshing(true); load();
                    }} tintColor="#00ff88" />
                }
            >
                {/* Header */}
                <View style={styles.header}>
                    <Text style={styles.ticker}>{ticker}</Text>
                    <Text style={styles.company}>{detail?.companyName}</Text>
                    <Text style={styles.sector}>{detail?.sector}</Text>
                </View>

                {/* Price Row */}
                <View style={styles.priceRow}>
                    <Text style={styles.price}>${detail?.price?.toFixed(2)}</Text>
                    <Text style={[styles.change, {color: isPositive ? '#00ff88' : '#ff4757'}]}>
                        {isPositive ? '▲' : '▼'} {Math.abs(detail?.changePct ?? 0).toFixed(2)}%
                    </Text>
                </View>

                {/* Fundamentals Grid */}
                <View style={styles.grid}>
                    {[
                        { label: 'P/E Ratio',    value: detail?.peRatio?.toFixed(1)    ?? '–' },
                        { label: 'Forward P/E',  value: detail?.forwardPe?.toFixed(1)  ?? '–' },
                        { label: 'P/B Ratio',    value: detail?.pbRatio?.toFixed(2)    ?? '–' },
                        { label: 'Mkt Cap',      value: formatMarketCap(detail?.marketCap) },
                    ].map(item => (
                        <View key={item.label} style={styles.gridItem}>
                            <Text style={styles.gridLabel}>{item.label}</Text>
                            <Text style={styles.gridValue}>{item.value}</Text>
                        </View>
                    ))}
                </View>

                {/* Sentiment Chart */}
                {sentiment.length > 1 && (
                    <View style={styles.chartSection}>
                        <Text style={styles.sectionTitle}>Sentiment Timeline</Text>
                        <LineChart
                            data={chartData}
                            width={SCREEN_WIDTH - 32}
                            height={200}
                            yAxisSuffix=""
                            yAxisInterval={1}
                            chartConfig={{
                                backgroundColor: 'transparent',
                                backgroundGradientFrom: 'rgba(0,0,0,0)',
                                backgroundGradientTo:   'rgba(0,0,0,0)',
                                decimalPlaces: 2,
                                color: (opacity = 1) => `rgba(0, 255, 136, ${opacity})`,
                                labelColor: (opacity = 1) => `rgba(255,255,255,${opacity * 0.6})`,
                                propsForDots: { r: '3', strokeWidth: '1', stroke: '#00ff88' },
                            }}
                            bezier
                            style={styles.chart}
                        />
                    </View>
                )}

                {/* Mention count per bucket */}
                <View style={styles.chartSection}>
                    <Text style={styles.sectionTitle}>Mention Volume</Text>
                    {sentiment.slice(-8).reverse().map((p, i) => (
                        <View key={i} style={styles.mentionRow}>
                            <Text style={styles.mentionTime}>
                                {new Date(p.bucket).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})}
                            </Text>
                            <View style={styles.mentionBarBg}>
                                <View style={[styles.mentionBar, {
                                    width: `${Math.min(100, (p.mentionCount / 50) * 100)}%`,
                                    backgroundColor: p.weightedSentiment > 0 ? '#00ff88' : '#ff4757',
                                }]} />
                            </View>
                            <Text style={styles.mentionCount}>{p.mentionCount}</Text>
                        </View>
                    ))}
                </View>
            </ScrollView>
        </LinearGradient>
    );
}

function formatMarketCap(cap?: number): string {
    if (!cap) return '–';
    if (cap >= 1e12) return `$${(cap / 1e12).toFixed(1)}T`;
    if (cap >= 1e9)  return `$${(cap / 1e9).toFixed(1)}B`;
    if (cap >= 1e6)  return `$${(cap / 1e6).toFixed(1)}M`;
    return `$${cap}`;
}

const styles = StyleSheet.create({
    scroll:       { padding: 16, paddingTop: 60 },
    center:       { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0c29' },
    header:       { marginBottom: 16 },
    ticker:       { color: '#fff', fontSize: 32, fontWeight: '900' },
    company:      { color: 'rgba(255,255,255,0.8)', fontSize: 16, fontWeight: '600' },
    sector:       { color: 'rgba(255,255,255,0.4)', fontSize: 13, marginTop: 2 },
    priceRow:     { flexDirection: 'row', alignItems: 'baseline', gap: 12, marginBottom: 24 },
    price:        { color: '#fff', fontSize: 28, fontWeight: '800' },
    change:       { fontSize: 18, fontWeight: '700' },
    grid:         { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 24 },
    gridItem:     { flex: 1, minWidth: '45%', backgroundColor: 'rgba(255,255,255,0.07)',
        borderRadius: 12, padding: 14 },
    gridLabel:    { color: 'rgba(255,255,255,0.5)', fontSize: 12, marginBottom: 4 },
    gridValue:    { color: '#fff', fontSize: 20, fontWeight: '800' },
    chartSection: { marginBottom: 24 },
    sectionTitle: { color: '#fff', fontSize: 16, fontWeight: '700', marginBottom: 12 },
    chart:        { borderRadius: 12 },
    mentionRow:   { flexDirection: 'row', alignItems: 'center', marginBottom: 8, gap: 8 },
    mentionTime:  { color: 'rgba(255,255,255,0.5)', fontSize: 11, width: 48 },
    mentionBarBg: { flex: 1, height: 8, backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: 4 },
    mentionBar:   { height: 8, borderRadius: 4 },
    mentionCount: { color: 'rgba(255,255,255,0.6)', fontSize: 12, width: 30, textAlign: 'right' },
});