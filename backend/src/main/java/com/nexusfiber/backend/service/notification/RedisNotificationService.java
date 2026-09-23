package com.nexusfiber.backend.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfiber.backend.controller.dto.WebSocketMessageDto;
import com.nexusfiber.backend.domain.ServiceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Distributed Notification Service
 * 
 * Architecture Note: We use Redis Pub/Sub instead of an in-memory event bus to broadcast messages.
 * This guarantees that if the application is scaled to multiple instances, a WebSocket client
 * connected to Instance A will still receive updates for a background task processing on Instance B.
 */
@Slf4j
@Service
public class RedisNotificationService implements NotificationService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String CHANNEL = "requests_updates";

    public RedisNotificationService(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Broadcasts a state change for a single active service request.
     * Implementation Detail: The Object payload is serialized to a JSON string before pushing to Redis.
     */
    @Override
    public Mono<Void> broadcastUpdate(ServiceRequest request) {
        WebSocketMessageDto msg = new WebSocketMessageDto("update", request);
        return publish(msg);
    }

    /**
     * Broadcasts a deletion event so connected frontends can immediately remove the item from the UI.
     */
    @Override
    public Mono<Void> broadcastDeleted(UUID id) {
        WebSocketMessageDto msg = new WebSocketMessageDto("deleted", Map.of("id", id.toString()));
        return publish(msg);
    }

    /**
     * Broadcasts a bulk deletion event to efficiently synchronize state across all connected clients.
     */
    @Override
    public Mono<Void> broadcastBulkDeleted(List<String> ids) {
        WebSocketMessageDto msg = new WebSocketMessageDto("deleted", Map.of("ids", ids));
        return publish(msg);
    }

    /**
     * Helper Method: Serializes the DTO and publishes it to the Redis channel.
     * Error Handling: If JSON serialization fails, it logs the error without crashing the application thread.
     */
    private Mono<Void> publish(WebSocketMessageDto msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            return redisTemplate.convertAndSend(CHANNEL, json)
                    .doOnSuccess(count -> log.debug("Published message to Redis channel: {}", CHANNEL))
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket message", e);
            return Mono.empty();
        }
    }
}
