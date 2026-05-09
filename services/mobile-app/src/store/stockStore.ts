import { create } from 'zustand';
import { StockRanking } from '../websocket/useStockWebSocket';

interface StockState {
    rankings:      StockRanking[];
    connected:     boolean;
    windowSeconds: number;
    maxPe:         number;
    setWindowSeconds: (w: number) => void;
    setMaxPe:         (pe: number) => void;
    setConnected:     (v: boolean) => void;
    updateRankings:   (updates: StockRanking[]) => void;
}

export const useStockStore = create<StockState>((set) => ({
    rankings:      [],
    connected:     false,
    windowSeconds: 3600,   // 1h default
    maxPe:         25,

    setWindowSeconds: (windowSeconds) => set({ windowSeconds, rankings: [] }),
    setMaxPe:         (maxPe)         => set({ maxPe }),
    setConnected:     (connected)     => set({ connected }),

    updateRankings: (updates) => set(state => {
        const map = new Map(state.rankings.map(r => [r.ticker, r]));
        updates.forEach(u => map.set(u.ticker, u));

        const sorted = [...map.values()]
            .sort((a, b) => b.compositeScore - a.compositeScore)
            .slice(0, 50);

        return { rankings: sorted };
    }),
}));