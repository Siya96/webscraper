import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { useStockStore } from '../store/stockStore';

const WINDOWS = [
    { label: '1h',    seconds: 3_600       },
    { label: '4h',    seconds: 14_400      },
    { label: '1d',    seconds: 86_400      },
    { label: '7d',    seconds: 604_800     },
    { label: '30d',   seconds: 2_592_000   },
    { label: '90d',   seconds: 7_776_000   },
    { label: '180d',  seconds: 15_552_000  },
    { label: '350d',  seconds: 30_240_000  },
];

export function TimeWindowPicker() {
    const windowSeconds    = useStockStore(s => s.windowSeconds);
    const setWindowSeconds = useStockStore(s => s.setWindowSeconds);

    return (
        <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.container}
            contentContainerStyle={styles.content}
        >
            {WINDOWS.map(w => {
                const active = windowSeconds === w.seconds;
                return (
                    <TouchableOpacity
                        key={w.label}
                        style={[styles.pill, active && styles.pillActive]}
                        onPress={() => setWindowSeconds(w.seconds)}
                    >
                        <Text style={[styles.pillText, active && styles.pillTextActive]}>
                            {w.label}
                        </Text>
                    </TouchableOpacity>
                );
            })}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container:     { paddingVertical: 10 },
    content:       { paddingHorizontal: 16, gap: 8, flexDirection: 'row' },
    pill:          { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20,
        backgroundColor: 'rgba(255,255,255,0.1)', borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.15)' },
    pillActive:    { backgroundColor: '#00ff88', borderColor: '#00ff88' },
    pillText:      { color: 'rgba(255,255,255,0.7)', fontWeight: '600', fontSize: 14 },
    pillTextActive:{ color: '#000', fontWeight: '800' },
});