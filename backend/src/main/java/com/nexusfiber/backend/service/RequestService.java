package com.nexusfiber.backend.service;

import com.nexusfiber.backend.controller.dto.BulkDeleteResponseDto;
import com.nexusfiber.backend.controller.dto.CreateServiceRequestDto;
import com.nexusfiber.backend.controller.dto.PaginatedResponse;
import com.nexusfiber.backend.controller.dto.ServiceRequestResponseDto;
import com.nexusfiber.backend.domain.RequestStatus;
import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.domain.User;
import com.nexusfiber.backend.domain.UserRole;
import com.nexusfiber.backend.event.RequestCreatedEvent;
import com.nexusfiber.backend.exception.ForbiddenException;
import com.nexusfiber.backend.exception.InvalidStateException;
import com.nexusfiber.backend.exception.ResourceNotFoundException;
import com.nexusfiber.backend.repository.ServiceRequestRepository;
import com.nexusfiber.backend.repository.UserRepository;
import com.nexusfiber.backend.service.notification.NotificationService;
import com.nexusfiber.backend.service.task.TaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RequestService {

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecutionService taskExecutionService;
    private final NotificationService notificationService;

    public RequestService(ServiceRequestRepository requestRepository, UserRepository userRepository,
                          ApplicationEventPublisher eventPublisher, TaskExecutionService taskExecutionService,
                          NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.taskExecutionService = taskExecutionService;
        this.notificationService = notificationService;
    }

    /**
     * Creates a new service request and initializes the background diagnostic workflow.
     * 
     * Architecture Note: We publish a RequestCreatedEvent here rather than executing the task directly.
     * This decouples the web thread from the background processor, ensuring the HTTP response
     * remains non-blocking and is returned to the client instantly.
     */
    public Mono<ServiceRequestResponseDto> createRequest(CreateServiceRequestDto dto, String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> {
                    ServiceRequest request = ServiceRequest.builder()
                            .id(UUID.randomUUID())
                            .customerAccount(dto.getCustomerAccount())
                            .requestType(dto.getRequestType())
                            .status(RequestStatus.PENDING.name())
                            .progress(0)
                            .operatorId(user.getId())
                            .createdAt(ZonedDateTime.now())
                            .updatedAt(ZonedDateTime.now())
                            .build();

                    return requestRepository.insertRequest(request)
                            .doOnNext(saved -> {
                                log.info("Service request '{}' created by operator '{}'", saved.getId(), username);
                                eventPublisher.publishEvent(new RequestCreatedEvent(this, saved, user));
                            })
                            .map(saved -> toDto(saved, user.getUsername()));
                });
    }

    /**
     * Retrieves a single service request.
     * Enforces ownership constraints (Operators can only view their own requests).
     */
    public Mono<ServiceRequestResponseDto> getRequest(UUID id, String username, UserRole role) {
        return findRequestWithOwner(id)
                .flatMap(pair -> {
                    assertOwnerOrSupervisor(pair.operator, username, role, "view");
                    return Mono.just(toDto(pair.request, pair.operator.getUsername()));
                });
    }

    /**
     * Cancels an actively running task.
     * 
     * State Guard: Cancelling is strictly restricted to tasks currently in PENDING or PROCESSING states.
     */
    public Mono<Void> cancelRequest(UUID id, String username, UserRole role) {
        return findRequestWithOwner(id)
                .flatMap(pair -> {
                    assertOwnerOrSupervisor(pair.operator, username, role, "cancel");

                    RequestStatus currentStatus = pair.request.getRequestStatus();
                    if (currentStatus != RequestStatus.PENDING && currentStatus != RequestStatus.PROCESSING) {
                        return Mono.error(new InvalidStateException("Cannot cancel request in state: " + currentStatus));
                    }

                    return taskExecutionService.cancelTask(id, pair.operator.getUsername());
                });
    }

    /**
     * Deletes a request from the system.
     * 
     * State Guard: To maintain data integrity, actively running diagnostic tasks cannot be deleted.
     * They must be explicitly cancelled first.
     */
    public Mono<Void> deleteRequest(UUID id, String username, UserRole role) {
        return findRequestWithOwner(id)
                .flatMap(pair -> {
                    assertOwnerOrSupervisor(pair.operator, username, role, "delete");

                    if (!pair.request.isTerminal()) {
                        return Mono.error(new InvalidStateException("Active diagnostic tasks cannot be deleted."));
                    }

                    return requestRepository.delete(pair.request)
                            .then(notificationService.broadcastDeleted(id));
                });
    }

    /**
     * Bulk deletes multiple requests.
     * Skips non-terminal (active) requests silently and returns the count of skipped items.
     */
    public Mono<BulkDeleteResponseDto> bulkDeleteRequests(List<String> ids, String username, UserRole role) {
        if (ids == null || ids.isEmpty()) return Mono.just(buildBulkResponse(List.of(), 0));

        List<UUID> uuids = ids.stream().map(UUID::fromString).collect(Collectors.toList());

        Flux<ServiceRequest> requestsFlux;
        if (role == UserRole.SUPERVISOR) {
            requestsFlux = requestRepository.findAllById(uuids);
        } else {
            requestsFlux = userRepository.findByUsername(username)
                    .flatMapMany(user -> requestRepository.findAllById(uuids)
                            .filter(req -> req.getOperatorId().equals(user.getId())));
        }

        return requestsFlux.collectList()
                .flatMap(requests -> {
                    List<ServiceRequest> terminal = requests.stream().filter(ServiceRequest::isTerminal).toList();
                    int activeSkipped = requests.size() - terminal.size();

                    if (terminal.isEmpty()) return Mono.just(buildBulkResponse(List.of(), activeSkipped));

                    return Flux.fromIterable(terminal)
                            .flatMap(req -> requestRepository.delete(req).thenReturn(req.getId().toString()))
                            .collectList()
                            .flatMap(deleted -> notificationService.broadcastBulkDeleted(deleted)
                                    .thenReturn(buildBulkResponse(deleted, activeSkipped)));
                });
    }

    /**
     * Returns a paginated list of service requests.
     * 
     * Performance Optimization: Resolves the N+1 query problem by collecting all unique Operator IDs
     * and fetching their user objects in a single batch query (findAllById) before mapping to DTOs.
     */
    public Mono<PaginatedResponse<ServiceRequestResponseDto>> listRequests(String username, UserRole role) {
        Flux<ServiceRequest> requestsFlux;

        if (role == UserRole.SUPERVISOR) {
            requestsFlux = requestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            requestsFlux = userRepository.findByUsername(username)
                    .flatMapMany(user -> requestRepository.findByOperatorIdOrderByCreatedAtDesc(user.getId()));
        }

        return requestsFlux.collectList()
                .flatMap(requests -> {
                    if (requests.isEmpty()) {
                        return Mono.just(PaginatedResponse.<ServiceRequestResponseDto>builder().count(0).results(List.of()).build());
                    }

                    // Batch fetch to prevent N+1 queries during mapping
                    Set<Long> operatorIds = requests.stream().map(ServiceRequest::getOperatorId).collect(Collectors.toSet());

                    return userRepository.findAllById(operatorIds)
                            .collectMap(User::getId, User::getUsername)
                            .map(userMap -> {
                                List<ServiceRequestResponseDto> dtos = requests.stream()
                                        .map(req -> toDto(req, userMap.getOrDefault(req.getOperatorId(), "unknown")))
                                        .toList();
                                return PaginatedResponse.<ServiceRequestResponseDto>builder().count(dtos.size()).results(dtos).build();
                            });
                });
    }

    // ─── Internal Helper Methods ───────────────────────────────────────────────────

    private Mono<RequestOwnerPair> findRequestWithOwner(UUID id) {
        return requestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Request not found with id: " + id)))
                .flatMap(request -> userRepository.findById(request.getOperatorId())
                        .map(operator -> new RequestOwnerPair(request, operator)));
    }

    private void assertOwnerOrSupervisor(User operator, String username, UserRole role, String action) {
        if (role != UserRole.SUPERVISOR && !operator.getUsername().equals(username)) {
            throw new ForbiddenException("You do not have permission to " + action + " this request.");
        }
    }

    private ServiceRequestResponseDto toDto(ServiceRequest req, String username) {
        return ServiceRequestResponseDto.builder()
                .id(req.getId().toString())
                .customerAccount(req.getCustomerAccount())
                .requestType(req.getRequestType())
                .status(req.getStatus())
                .progress(req.getProgress())
                .operatorUsername(username)
                .createdAt(req.getCreatedAt() != null ? req.getCreatedAt().toString() : null)
                .build();
    }

    private BulkDeleteResponseDto buildBulkResponse(List<String> deletedIds, int activeSkipped) {
        return BulkDeleteResponseDto.builder()
                .deletedIds(deletedIds)
                .activeSkippedCount(activeSkipped)
                .message("Successfully deleted " + deletedIds.size() + " request(s). Skipped " + activeSkipped + " active task(s).")
                .build();
    }

    private record RequestOwnerPair(ServiceRequest request, User operator) {}
}
