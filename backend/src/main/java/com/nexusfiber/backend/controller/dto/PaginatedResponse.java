package com.nexusfiber.backend.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Generic paginated response wrapper.
 * <p>
 * Designed to mirror the Django REST Framework pagination format for frontend compatibility.
 * The {@code next} and {@code previous} fields are reserved for cursor-based pagination
 * which can be implemented as a future enhancement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private int count;

    @Builder.Default
    private String next = null;

    @Builder.Default
    private String previous = null;

    private List<T> results;
}
