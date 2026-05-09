import React, { useState, useCallback } from 'react';
import {
    View, Text, TextInput, FlatList,
    TouchableOpacity, StyleSheet, ActivityIndicator
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useStockStore } from '../store/stockStore';
import { StockCard } from '../components/StockCard';
import { debounce } from '../utils/debounce';

const API = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080';

export default function SearchScreen() {
    const router        = useRouter();
    const windowSeconds = useStockStore(s => s.windowSeconds);
    const maxPe         = useStockStore(s => s.maxPe);
    const setMaxPe      = useStockStore(s => s.setMaxPe);

    const [query,   setQuery]   = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);

    const search = useCallback(
        debounce(async (q: string) => {
            if (!q.trim()) { setResults([]); return; }
            setLoading(true);
            try {
                const res = await fetch(
                    `${API}/api/v1/stocks/search?q=${encodeURIComponent(q)}&windowSeconds=${windowSeconds}`
                );
                setResults(await res.json());
            } finally {
                setLoading(false);
            }
        }, 400),
        [windowSeconds]
    );

    const PE_PRESETS = [10, 15, 20, 25, 30, 50];

    return (
        <LinearGradient colors={['#0f0c29','#302b63','#24243e']} style={{flex:1}}>
            <View style={styles.container}>
                <Text style={styles.title}>Search Stocks</Text>

                {/* Search Input */}
                <TextInput
                    style={styles.input}
                    placeholder="Search ticker or company..."
                    placeholderTextColor="rgba(255,255,255,0.3)"
                    value={query}
                    onChangeText={q => { setQuery(q); search(q); }}
                    autoCapitalize="characters"
                    returnKeyType="search"
                />

                {/* P/E Filter */}
                <Text style={styles.filterLabel}>Max P/E Ratio</Text>
                <View style={styles.peRow}>
                    {PE_PRESETS.map(pe => (
                        <TouchableOpacity
                            key={pe}
                            style={[styles.pePill, maxPe === pe && styles.pePillActive]}
                            onPress={() => setMaxPe(pe)}
                        >
                            <Text style={[styles.pePillText, maxPe === pe && styles.pePillTextActive]}>
                                {pe}
                            </Text>
                        </TouchableOpacity>
                    ))}
                </View>

                {loading && <ActivityIndicator color="#00ff88" style={{ marginTop: 20 }} />}

                <FlatList
                    data={results}
                    keyExtractor={(item: any) => item.ticker}
                    renderItem={({ item, index }) => (
                        <StockCard ranking={item} rank={index + 1} />
                    )}
                    showsVerticalScrollIndicator={false}
                    contentContainerStyle={{ paddingBottom: 100 }}
                />
            </View>
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container:       { flex: 1, paddingHorizontal: 16, paddingTop: 60 },
    title:           { color: '#fff', fontSize: 24, fontWeight: '800', marginBottom: 16 },
    input:           { backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: 12,
        padding: 14, color: '#fff', fontSize: 16, marginBottom: 16,
        borderWidth: 1, borderColor: 'rgba(255,255,255,0.15)' },
    filterLabel:     { color: 'rgba(255,255,255,0.6)', fontSize: 13, marginBottom: 8, fontWeight: '600' },
    peRow:           { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 20 },
    pePill:          { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20,
        backgroundColor: 'rgba(255,255,255,0.1)',
        borderWidth: 1, borderColor: 'rgba(255,255,255,0.15)' },
    pePillActive:    { backgroundColor: '#00ff88', borderColor: '#00ff88' },
    pePillText:      { color: 'rgba(255,255,255,0.7)', fontWeight: '600' },
    pePillTextActive:{ color: '#000', fontWeight: '800' },
});