package com.nexusfiber.backend.event;

import com.nexusfiber.backend.domain.ServiceRequest;
import com.nexusfiber.backend.domain.User;
import org.springframework.context.ApplicationEvent;

public class RequestCreatedEvent extends ApplicationEvent {
    private final ServiceRequest serviceRequest;
    private final User user;

    public RequestCreatedEvent(Object source, ServiceRequest serviceRequest, User user) {
        super(source);
        this.serviceRequest = serviceRequest;
        this.user = user;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public User getUser() {
        return user;
    }
}
