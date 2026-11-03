package com.dsk.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What a client is allowed to send. Deliberately excludes {@code id} and
 * {@code createdAt}: neither is client-controlled, and Jackson ignores the
 * extra fields the UI echoes back on update.
 */
public record TaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        TaskStatus status,

        LocalDate dueDate
) {
}
