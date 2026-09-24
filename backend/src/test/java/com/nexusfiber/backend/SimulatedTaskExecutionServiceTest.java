package com.nexusfiber.backend;

import com.nexusfiber.backend.domain.RequestStatus;
import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.repository.ServiceRequestRepository;
import com.nexusfiber.backend.service.notification.NotificationService;
import com.nexusfiber.backend.service.task.SimulatedTaskExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedTaskExecutionServiceTest {

    @Mock
    private ServiceRequestRepository requestRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SimulatedTaskExecutionService taskExecutionService;

    @Test
    void testCancelTask_Success() {
        UUID id = UUID.randomUUID();
        
        ServiceRequest request = new ServiceRequest();
        request.setId(id);
        request.setRequestStatus(RequestStatus.PROCESSING);
        request.setProgress(50);

        when(requestRepository.findById(id)).thenReturn(Mono.just(request));
        when(requestRepository.save(any(ServiceRequest.class))).thenReturn(Mono.just(request));
        when(notificationService.broadcastUpdate(any(ServiceRequest.class)))
                .thenReturn(Mono.empty());

        Mono<Void> resultMono = taskExecutionService.cancelTask(id, "operator1");

        StepVerifier.create(resultMono)
                .verifyComplete();
    }
}
