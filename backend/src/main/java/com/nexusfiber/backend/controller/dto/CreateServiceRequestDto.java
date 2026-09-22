package com.nexusfiber.backend.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class CreateServiceRequestDto {
    @NotBlank
    @JsonProperty("customer_account")
    private String customerAccount;
    
    @NotBlank
    @JsonProperty("request_type")
    private String requestType;
}
