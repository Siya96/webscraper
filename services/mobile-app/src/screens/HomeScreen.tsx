import React, { useMemo } from 'react';
import {
    View, Text, StyleSheet, FlatList,
    TouchableOpacity, StatusBar, Switch
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useStockStore }    from '../store/stockStore';
import { useStockWebSocket } from '../websocket/useStockWebSocket';
import { StockCard }         from '../components/StockCard';
import { TimeWindowPicker }  from '../components/TimeWindowPicker';
import { ConnectionStatus }  from '../components/ConnectionStatus';

export default function HomeScreen() {
    const rankings      = useStockStore(s => s.rankings);
    const windowSeconds = useStockStore(s => s.windowSeconds);
    const maxPe         = useStockStore(s => s.maxPe);
    const connected     = useStockStore(s => s.connected);

    useStockWebSocket({ windowSeconds, maxPe });

    const renderItem = ({ item, index }: any) => (
        <StockCard ranking={item} rank={index + 1} />
    );

    return (
        <LinearGradient colors={['#0f0c29', '#302b63', '#24243e']} style={styles.container}>
            <StatusBar barStyle="light-content" />

            <View style={styles.header}>
                <Text style={styles.title}>📈 StockPulse</Text>
                <ConnectionStatus connected={connected} />
            </View>

            <TimeWindowPicker />

            <FlatList
                data={rankings}
                keyExtractor={item => item.ticker}
                renderItem={renderItem}
                showsVerticalScrollIndicator={false}
                contentContainerStyle={styles.list}
                initialNumToRender={10}
                maxToRenderPerBatch={5}
                removeClippedSubviews
            />
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    header:    { flexDirection: 'row', justifyContent: 'space-between',
        alignItems: 'center', paddingHorizontal: 20, paddingTop: 60, paddingBottom: 10 },
    title:     { color: '#fff', fontSize: 24, fontWeight: '800' },
    list:      { paddingHorizontal: 16, paddingBottom: 100 },
});