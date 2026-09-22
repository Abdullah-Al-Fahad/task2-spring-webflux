package com.nexusfiber.backend.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceRequestResponseDto {
    private String id;

    @JsonProperty("customer_account")
    private String customerAccount;

    @JsonProperty("request_type")
    private String requestType;

    private String status;
    private Integer progress;

    @JsonProperty("operator_username")
    private String operatorUsername;

    @JsonProperty("created_at")
    private String createdAt;
}
