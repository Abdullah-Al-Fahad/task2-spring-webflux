package com.nexusfiber.backend.repository;

import com.nexusfiber.backend.domain.ServiceRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Custom R2DBC Repository Implementation
 * 
 * Architecture Note: Spring Data R2DBC provides standard CRUD methods out of the box.
 * However, we implemented this custom method to forcefully return the exact saved Entity
 * after an insert, ensuring we have the auto-generated database timestamps available instantly.
 */
@Repository
public class ServiceRequestRepositoryImpl implements ServiceRequestRepositoryCustom {

    private final DatabaseClient databaseClient;

    public ServiceRequestRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Executes a raw, non-blocking SQL insert statement.
     * 
     * Performance Optimization: Binds all parameters safely to prevent SQL injection.
     * It then immediately maps the inserted row back into the Java ServiceRequest object.
     */
    @Override
    public Mono<ServiceRequest> insertRequest(ServiceRequest request) {
        String sql = "INSERT INTO service_requests (id, customer_account, request_type, status, progress, operator_id, created_at, updated_at) " +
                     "VALUES (:id, :customerAccount, :requestType, :status, :progress, :operatorId, :createdAt, :updatedAt)";

        return databaseClient.sql(sql)
                .bind("id", request.getId())
                .bind("customerAccount", request.getCustomerAccount())
                .bind("requestType", request.getRequestType())
                .bind("status", request.getStatus())
                .bind("progress", request.getProgress())
                .bind("operatorId", request.getOperatorId())
                .bind("createdAt", request.getCreatedAt())
                .bind("updatedAt", request.getUpdatedAt())
                .fetch()
                .rowsUpdated()
                .thenReturn(request); // Returns the original object downstream in the reactive pipeline once the DB insert completes
    }
}
