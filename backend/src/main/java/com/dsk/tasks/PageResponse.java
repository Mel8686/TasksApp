package com.dsk.tasks;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope. Spring Data's {@code Page} serialises differently
 * between versions, so it is mapped to this record instead of being returned.
 */
public record PageResponse<T>(List<T> content, int page, int size, long total, int totalPages) {

    static <T> PageResponse<T> of(Page<T> result) {
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
