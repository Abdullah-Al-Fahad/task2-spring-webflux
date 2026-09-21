package com.nexusfiber.backend.repository;

import com.nexusfiber.backend.domain.ServiceRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface ServiceRequestRepository extends ReactiveCrudRepository<ServiceRequest, UUID>, ServiceRequestRepositoryCustom {
    Flux<ServiceRequest> findByOperatorIdOrderByCreatedAtDesc(Long operatorId);
    Flux<ServiceRequest> findAllByOrderByCreatedAtDesc();
}
