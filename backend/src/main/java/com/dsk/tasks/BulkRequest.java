package com.dsk.tasks;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A bulk request names rows, not versions. {@code If-Match} is deliberately not
 * accepted: one stale row would abort the whole batch, so the operation wins.
 * All or nothing — if any id fails, nothing is written.
 */
public record BulkRequest(
        @NotEmpty(message = "ids must not be empty")
        @Size(max = 100, message = "ids may contain at most 100 entries")
        List<@NotNull(message = "ids must not contain null") Long> ids,

        @NotNull(message = "operation is required")
        BulkOperation operation,

        TaskStatus status
) {
}
