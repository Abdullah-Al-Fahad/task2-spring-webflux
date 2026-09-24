package com.nexusfiber.backend;

import com.nexusfiber.backend.domain.RequestStatus;
import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.domain.User;
import com.nexusfiber.backend.domain.UserRole;
import com.nexusfiber.backend.controller.dto.CreateServiceRequestDto;
import com.nexusfiber.backend.controller.dto.ServiceRequestResponseDto;
import com.nexusfiber.backend.event.RequestCreatedEvent;
import com.nexusfiber.backend.exception.InvalidStateException;
import com.nexusfiber.backend.repository.ServiceRequestRepository;
import com.nexusfiber.backend.repository.UserRepository;
import com.nexusfiber.backend.service.notification.NotificationService;
import com.nexusfiber.backend.service.RequestService;
import com.nexusfiber.backend.service.task.SimulatedTaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private ServiceRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SimulatedTaskExecutionService taskExecutionService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RequestService requestService;

    @Test
    void testCreateRequest_Success() {
        String username = "operator1";
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername(username);
        mockUser.setRole(UserRole.OPERATOR.name());

        CreateServiceRequestDto dto = new CreateServiceRequestDto();
        dto.setCustomerAccount("CUST-123");
        dto.setRequestType("LINE_DIAGNOSTIC");

        ServiceRequest savedRequest = new ServiceRequest();
        savedRequest.setId(UUID.randomUUID());
        savedRequest.setCustomerAccount(dto.getCustomerAccount());
        savedRequest.setRequestType(dto.getRequestType());
        savedRequest.setOperatorId(mockUser.getId());
        savedRequest.setRequestStatus(RequestStatus.PENDING);

        when(userRepository.findByUsername(username)).thenReturn(Mono.just(mockUser));
        when(requestRepository.insertRequest(any(ServiceRequest.class))).thenReturn(Mono.just(savedRequest));

        Mono<ServiceRequestResponseDto> resultMono = requestService.createRequest(dto, username);

        StepVerifier.create(resultMono)
                .assertNext(response -> {
                    assertNotNull(response.getId());
                    assertEquals("CUST-123", response.getCustomerAccount());
                    assertEquals("LINE_DIAGNOSTIC", response.getRequestType());
                    assertEquals(username, response.getOperatorUsername());
                })
                .verifyComplete();
    }

    @Test
    void testCancelRequest_Success() {
        UUID id = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("operator1");

        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setOperatorId(1L);
        request.setRequestStatus(RequestStatus.PROCESSING);

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser));
        when(taskExecutionService.cancelTask(id, "operator1")).thenReturn(Mono.empty());

        Mono<Void> resultMono = requestService.cancelRequest(id, "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .verifyComplete();
    }

    @Test
    void testCancelRequest_InvalidStateThrowsException() {
        UUID id = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("operator1");

        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setOperatorId(1L);
        request.setRequestStatus(RequestStatus.COMPLETED);

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser));

        Mono<Void> resultMono = requestService.cancelRequest(id, "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .expectError(InvalidStateException.class)
                .verify();
    }

    @Test
    void testDeleteRequest_Success() {
        UUID id = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("operator1");

        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setOperatorId(1L);
        request.setRequestStatus(RequestStatus.CANCELLED);

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser));
        when(requestRepository.delete(request)).thenReturn(Mono.empty());
        when(notificationService.broadcastDeleted(id)).thenReturn(Mono.empty());

        Mono<Void> resultMono = requestService.deleteRequest(id, "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .verifyComplete();
    }

    @Test
    void testDeleteRequest_ActiveTaskThrowsException() {
        UUID id = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("operator1");

        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setOperatorId(1L);
        request.setRequestStatus(RequestStatus.PROCESSING);

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser));

        Mono<Void> resultMono = requestService.deleteRequest(id, "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .expectError(InvalidStateException.class)
                .verify();
    }

    @Test
    void testGetRequest_Success() {
        UUID id = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("operator1");

        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setOperatorId(1L);
        request.setCustomerAccount("CUST-123");

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(userRepository.findById(1L)).thenReturn(Mono.just(mockUser));

        Mono<com.nexusfiber.backend.controller.dto.ServiceRequestResponseDto> resultMono = requestService.getRequest(id, "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .assertNext(res -> assertEquals("CUST-123", res.getCustomerAccount()))
                .verifyComplete();
    }

    @Test
    void testListRequests_Supervisor() {
        ServiceRequest req1 = new ServiceRequest(); req1.setId(UUID.randomUUID()); req1.setOperatorId(1L);
        ServiceRequest req2 = new ServiceRequest(); req2.setId(UUID.randomUUID()); req2.setOperatorId(2L);
        
        when(requestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(reactor.core.publisher.Flux.just(req1, req2));
        
        User u1 = new User(); u1.setId(1L); u1.setUsername("u1");
        User u2 = new User(); u2.setId(2L); u2.setUsername("u2");
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anySet())).thenReturn(reactor.core.publisher.Flux.just(u1, u2));

        Mono<com.nexusfiber.backend.controller.dto.PaginatedResponse<com.nexusfiber.backend.controller.dto.ServiceRequestResponseDto>> resultMono = requestService.listRequests("super", UserRole.SUPERVISOR);

        StepVerifier.create(resultMono)
                .assertNext(res -> assertEquals(2, res.getCount()))
                .verifyComplete();
    }

    @Test
    void testBulkDeleteRequests_Success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        ServiceRequest req1 = new ServiceRequest(); req1.setId(id1); req1.setRequestStatus(RequestStatus.COMPLETED); req1.setOperatorId(1L);
        ServiceRequest req2 = new ServiceRequest(); req2.setId(id2); req2.setRequestStatus(RequestStatus.PROCESSING); req2.setOperatorId(1L);
        
        User mockUser = new User(); mockUser.setId(1L); mockUser.setUsername("operator1");
        
        when(userRepository.findByUsername("operator1")).thenReturn(Mono.just(mockUser));
        when(requestRepository.findAllById(java.util.List.of(id1, id2))).thenReturn(reactor.core.publisher.Flux.just(req1, req2));
        when(requestRepository.delete(req1)).thenReturn(Mono.empty());
        when(notificationService.broadcastBulkDeleted(java.util.List.of(id1.toString()))).thenReturn(Mono.empty());

        Mono<com.nexusfiber.backend.controller.dto.BulkDeleteResponseDto> resultMono = requestService.bulkDeleteRequests(java.util.List.of(id1.toString(), id2.toString()), "operator1", UserRole.OPERATOR);

        StepVerifier.create(resultMono)
                .assertNext(res -> {
                    assertEquals(1, res.getDeletedIds().size());
                    assertEquals(1, res.getActiveSkippedCount());
                })
                .verifyComplete();
    }
}
