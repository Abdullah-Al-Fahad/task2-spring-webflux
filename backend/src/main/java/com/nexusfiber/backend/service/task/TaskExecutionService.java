package com.nexusfiber.backend.service.task;

import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.domain.User;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TaskExecutionService {
    Mono<Void> executeTask(ServiceRequest request);
    Mono<Void> cancelTask(UUID requestId, String operatorUsername);
}
