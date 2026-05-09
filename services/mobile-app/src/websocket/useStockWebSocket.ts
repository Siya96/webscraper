import { useEffect, useRef, useCallback } from 'react';
import { useStockStore } from '../store/stockStore';

export interface StockRanking {
    ticker: string;
    companyName: string;
    compositeScore: number;
    sentimentScore: number;
    momentumScore: number;
    velocityScore: number;
    peRatio: number;
    forwardPe: number;
    mentionCount: number;
    windowSeconds: number;
    calculatedAt: string;
}

interface UseStockWebSocketOptions {
    windowSeconds: number;
    maxPe: number;
}

const WS_URL = process.env.EXPO_PUBLIC_WS_URL || 'ws://localhost:8085/ws/rankings';

export function useStockWebSocket({ windowSeconds, maxPe }: UseStockWebSocketOptions) {
    const wsRef          = useRef<WebSocket | null>(null);
    const reconnectTimer = useRef<NodeJS.Timeout>();
    const updateRankings = useStockStore(s => s.updateRankings);
    const setConnected   = useStockStore(s => s.setConnected);

    const connect = useCallback(() => {
        const url = `${WS_URL}?window=${windowSeconds}&maxPe=${maxPe}`;
        const ws  = new WebSocket(url);

        ws.onopen = () => {
            setConnected(true);
            console.log('WS connected');
        };

        ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg.type === 'ranking') {
                    updateRankings([msg.data as StockRanking]);
                }
            } catch (e) {
                console.warn('WS parse error:', e);
            }
        };

        ws.onerror  = () => setConnected(false);
        ws.onclose  = () => {
            setConnected(false);
            // Exponential backoff reconnect
            reconnectTimer.current = setTimeout(connect, 3000);
        };

        wsRef.current = ws;
    }, [windowSeconds, maxPe]);

    useEffect(() => {
        connect();
        return () => {
            clearTimeout(reconnectTimer.current);
            wsRef.current?.close();
        };
    }, [connect]);
}