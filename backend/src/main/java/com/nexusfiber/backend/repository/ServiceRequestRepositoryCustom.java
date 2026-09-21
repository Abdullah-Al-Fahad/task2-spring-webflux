package com.nexusfiber.backend.repository;

import com.nexusfiber.backend.domain.ServiceRequest;
import reactor.core.publisher.Mono;

public interface ServiceRequestRepositoryCustom {
    Mono<ServiceRequest> insertRequest(ServiceRequest request);
}
