package com.nexusfiber.backend.service.notification;

import com.nexusfiber.backend.domain.ServiceRequest;
import reactor.core.publisher.Mono;

public interface NotificationService {
    Mono<Void> broadcastUpdate(ServiceRequest request);
    Mono<Void> broadcastDeleted(java.util.UUID id);
    Mono<Void> broadcastBulkDeleted(java.util.List<String> ids);
}
