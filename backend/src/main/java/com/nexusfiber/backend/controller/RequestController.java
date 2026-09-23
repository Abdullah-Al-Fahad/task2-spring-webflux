package com.nexusfiber.backend.controller;

import com.nexusfiber.backend.controller.dto.BulkDeleteRequestDto;
import com.nexusfiber.backend.controller.dto.BulkDeleteResponseDto;
import com.nexusfiber.backend.controller.dto.CreateServiceRequestDto;
import com.nexusfiber.backend.controller.dto.PaginatedResponse;
import com.nexusfiber.backend.controller.dto.ServiceRequestResponseDto;
import com.nexusfiber.backend.domain.UserRole;
import com.nexusfiber.backend.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * REST controller for service request lifecycle management.
 * Acts as the HTTP entrypoint, delegating all domain logic to the RequestService.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Initializes a new service request.
     * Implementation Note: @Valid enforces DTO constraint validation before executing controller logic.
     */
    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ServiceRequestResponseDto> createRequest(
            @Valid @RequestBody Mono<CreateServiceRequestDto> dtoMono,
            Authentication authentication) {
        return dtoMono.flatMap(dto -> requestService.createRequest(dto, authentication.getName()));
    }

    /**
     * Returns a paginated list of service requests filtered by the authenticated user's role context.
     */
    @GetMapping("/")
    public Mono<PaginatedResponse<ServiceRequestResponseDto>> listRequests(Authentication authentication) {
        return requestService.listRequests(authentication.getName(), extractRole(authentication));
    }

    @GetMapping("/{id}/")
    public Mono<ServiceRequestResponseDto> getRequest(@PathVariable UUID id, Authentication authentication) {
        return requestService.getRequest(id, authentication.getName(), extractRole(authentication));
    }

    @PostMapping("/{id}/cancel/")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancelRequest(@PathVariable UUID id, Authentication authentication) {
        return requestService.cancelRequest(id, authentication.getName(), extractRole(authentication));
    }

    @DeleteMapping("/{id}/")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRequest(@PathVariable UUID id, Authentication authentication) {
        return requestService.deleteRequest(id, authentication.getName(), extractRole(authentication));
    }

    @PostMapping("/bulk-delete/")
    public Mono<BulkDeleteResponseDto> bulkDeleteRequests(
            @Valid @RequestBody Mono<BulkDeleteRequestDto> dtoMono,
            Authentication authentication) {
        return dtoMono.flatMap(dto ->
                requestService.bulkDeleteRequests(dto.getIds(), authentication.getName(), extractRole(authentication)));
    }

    /**
     * Helper method to parse the UserRole from the Spring Security Authentication principal.
     */
    private UserRole extractRole(Authentication authentication) {
        String roleName = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse(UserRole.OPERATOR.name());
        return UserRole.valueOf(roleName);
    }
}
