package com.nexusfiber.backend.websocket;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Real-Time WebSocket Handler
 * 
 * Architecture Note: This class listens to Redis Pub/Sub and pipes the messages directly
 * to the connected React frontend. Because it listens to Redis, any backend server instance
 * can publish an update, and ALL instances will push it to their respective connected WebSockets.
 * This guarantees horizontal scalability.
 */
@Component
public class RequestWebSocketHandler implements WebSocketHandler {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String CHANNEL = "requests_updates";

    public RequestWebSocketHandler(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * This method is called once every time a new client (browser) connects to the WebSocket endpoint.
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        
        // 1. Subscribe to the 'requests_updates' channel in Redis
        return redisTemplate.listenToChannel(CHANNEL)
                
                // 2. Extract the actual JSON string from the Redis Message wrapper
                .map(message -> message.getMessage())
                
                // 3. Convert the JSON string into a Spring WebSocket text message
                .map(session::textMessage)
                
                // 4. Pipe that message stream out to the client browser!
                .as(session::send)
                
                // 5. If an error occurs (e.g. Redis disconnects), log it and gracefully close the socket
                .doOnError(error -> System.err.println("WebSocket Error: " + error.getMessage()));
    }
}
