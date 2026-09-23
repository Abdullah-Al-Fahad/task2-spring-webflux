package com.nexusfiber.backend.service.task;

import com.nexusfiber.backend.domain.RequestStatus;
import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.repository.ServiceRequestRepository;
import com.nexusfiber.backend.service.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class SimulatedTaskExecutionService implements TaskExecutionService {

    private final ServiceRequestRepository requestRepository;
    private final NotificationService notificationService;
    
    /**
     * Thread Safety Note:
     * We use a ConcurrentHashMap to track active task subscriptions. This ensures thread safety
     * if multiple asynchronous operations (like cancellation or status checks) occur concurrently.
     */
    private final ConcurrentMap<UUID, Disposable> activeTasks = new ConcurrentHashMap<>();

    public SimulatedTaskExecutionService(ServiceRequestRepository requestRepository, NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
    }

    /**
     * Executes the background diagnostic task simulation.
     * Replaces legacy synchronous task queues by utilizing Project Reactor's non-blocking Flux.interval.
     */
    @Override
    public Mono<Void> executeTask(ServiceRequest request) {
        UUID id = request.getId();
        log.info("Starting diagnostic task for request '{}'", id);

        return updateRequestState(request, RequestStatus.PROCESSING, 0, "[INFO] Task initialized in queue...")
                .doOnSuccess(saved -> {
                    Disposable task = Flux.interval(Duration.ofMillis(500))
                            .take(10)
                            .map(tick -> (tick.intValue() + 1) * 10)
                            .concatMap(progress -> updateRequestState(saved, 
                                    progress == 100 ? RequestStatus.COMPLETED : RequestStatus.PROCESSING, 
                                    progress, getLogMessage(saved, progress)))
                            .doOnComplete(() -> {
                                log.info("Task '{}' completed successfully", id);
                                activeTasks.remove(id); // Memory cleanup
                            })
                            .subscribe();

                    activeTasks.put(id, task);
                })
                .then();
    }

    /**
     * Cancels an actively running task by safely disposing its reactive stream subscription.
     */
    @Override
    public Mono<Void> cancelTask(UUID id, String operatorUsername) {
        return Mono.fromRunnable(() -> {
            Disposable task = activeTasks.remove(id);
            if (task != null && !task.isDisposed()) {
                task.dispose();
                log.info("Task '{}' execution cancelled in memory", id);
            } else {
                log.warn("Task '{}' not found in active tasks memory", id);
            }
        }).then(
            requestRepository.findById(id)
                .flatMap(request -> updateRequestState(request, RequestStatus.CANCELLED, request.getProgress(), "[ERROR] Task manually cancelled by operator."))
                .then()
        );
    }

    private String getLogMessage(ServiceRequest req, int progress) {
        String type = req.getRequestType();
        if ("LINE_DIAGNOSTIC".equals(type)) {
            if (progress == 20) return "[INFO] Pinging customer modem (ICMP echo)...";
            if (progress == 40) return "[INFO] Analyzing packet loss and latency metrics...";
            if (progress == 60) return "[INFO] Checking upstream/downstream SNR levels...";
            if (progress == 80) return "[INFO] Running DOCSIS channel bonding verification...";
            if (progress == 100) return "[SUCCESS] Line diagnostic passed. Signal within normal parameters.";
        } else if ("FIRMWARE_UPGRADE".equals(type)) {
            if (progress == 20) return "[INFO] Connecting to customer modem for " + req.getCustomerAccount() + "...";
            if (progress == 40) return "[INFO] Downloading firmware payload v4.2.1 from vendor server...";
            if (progress == 60) return "[WARN] Connection unstable. Retrying flash sequence...";
            if (progress == 80) return "[INFO] Flash successful. Initiating remote reboot...";
            if (progress == 100) return "[SUCCESS] Modem online. Firmware version verified.";
        } else if ("NETWORK_PROVISION".equals(type)) {
            if (progress == 20) return "[INFO] Validating MAC address format...";
            if (progress == 40) return "[INFO] Allocating dynamic IP from DHCP pool...";
            if (progress == 60) return "[INFO] Pushing configuration to local neighborhood switch...";
            if (progress == 80) return "[INFO] Testing automated radius authentication...";
            if (progress == 100) return "[SUCCESS] Provisioning complete. Account activated on network.";
        }
        return null;
    }

    private Mono<ServiceRequest> updateRequestState(ServiceRequest request, RequestStatus status, int progress, String logMessage) {
        request.setRequestStatus(status);
        request.setProgress(progress);
        request.setLogMessage(logMessage);
        request.setUpdatedAt(ZonedDateTime.now());
        
        // Architecture Optimization: Only persist to DB on significant state changes to save I/O overhead.
        // For intermediate progress (10% to 90%), we bypass the database and only broadcast via Redis.
        if (progress == 0 || progress == 100 || status == RequestStatus.CANCELLED) {
            return requestRepository.save(request)
                    .flatMap(saved -> notificationService.broadcastUpdate(saved).thenReturn(saved));
        } else {
            return notificationService.broadcastUpdate(request).thenReturn(request);
        }
    }
}
