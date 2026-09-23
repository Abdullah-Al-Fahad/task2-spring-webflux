package com.nexusfiber.backend.event;

import com.nexusfiber.backend.service.task.TaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Event Listener Component for Service Requests
 * 
 * Architecture Note: This listener implements the Publish-Subscribe pattern internally within the JVM.
 * It intercepts domain events published by the RequestService and triggers the background task processor.
 */
@Slf4j
@Component
public class RequestEventListener {

    private final TaskExecutionService taskExecutionService;

    public RequestEventListener(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    /**
     * Intercepts the RequestCreatedEvent as soon as it is fired.
     * 
     * Implementation Detail: @Async allows this listener to run in a separate thread pool.
     * We subscribe the reactive task execution onto the 'boundedElastic' scheduler,
     * which is specifically optimized in WebFlux for blocking/background workloads
     * so it doesn't starve the primary Netty I/O event loop.
     */
    @Async
    @EventListener
    public void handleRequestCreated(RequestCreatedEvent event) {
        log.info("Event received: Request {} created by {}", 
                 event.getServiceRequest().getId(), event.getUser().getUsername());

        taskExecutionService.executeTask(event.getServiceRequest())
                .subscribeOn(Schedulers.boundedElastic()) // Offload the workload to background threads
                .subscribe(); // Trigger the reactive stream to actually start executing
    }
}
