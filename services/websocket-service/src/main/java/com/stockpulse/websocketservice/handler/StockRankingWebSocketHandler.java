package com.stockpulse.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.common.model.StockRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockRankingWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper mapper;

    // One sink per connected session (or per "window" subscription)
    private final Map<String, Sinks.Many<StockRanking>> sessionSinks = new ConcurrentHashMap<>();

    // Global sink — all connected clients get live rankings
    private final Sinks.Many<StockRanking> globalSink = Sinks.many()
            .multicast()
            .onBackpressureBuffer(1024);

    @Override
    public reactor.core.publisher.Mono<Void> handle(WebSocketSession session) {
        var sessionId = session.getId();
        log.info("WS client connected: {}", sessionId);

        // Parse subscription filter from query string (e.g. ?window=3600&maxPe=20)
        var params = parseQueryParams(session.getHandshakeInfo().getUri().getQuery());
        long windowSec = Long.parseLong(params.getOrDefault("window", "3600"));
        double maxPe   = Double.parseDouble(params.getOrDefault("maxPe", "25"));

        var filtered = globalSink.asFlux()
                .filter(r -> r.windowSeconds() == windowSec)
                .filter(r -> r.peRatio() <= maxPe && r.peRatio() > 0)
                .map(this::toJson)
                .map(session::textMessage);

        // Send heartbeat every 30s to detect dead connections
        var heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(i -> session.textMessage("{\"type\":\"ping\"}"));

        return session.send(Flux.merge(filtered, heartbeat))
                .doFinally(sig -> {
                    log.info("WS client disconnected: {}", sessionId);
                    sessionSinks.remove(sessionId);
                });
    }

    @KafkaListener(topics = "live-rankings", groupId = "websocket-group")
    public void onLiveRanking(ConsumerRecord<String, StockRanking> record) {
        var result = globalSink.tryEmitNext(record.value());
        if (result.isFailure()) {
            log.warn("Failed to emit to WS sink: {}", result);
        }
    }

    private String toJson(StockRanking r) {
        try {
            return mapper.writeValueAsString(Map.of("type", "ranking", "data", r));
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        if (query == null) return Map.of();
        var map = new ConcurrentHashMap<String, String>();
        for (var pair : query.split("&")) {
            var kv = pair.split("=");
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }
}