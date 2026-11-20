package com.chubb.claims.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable paging envelope so every list endpoint returns the same shape. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
