package com.dsk.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * What a client is allowed to send. Deliberately excludes {@code id} and
 * {@code createdAt}: neither is client-controlled, and Jackson ignores the
 * extra fields the UI echoes back on update.
 *
 * <p>{@code tags} is a full replacement, like {@code dueDate}: null clears the
 * tags. Names are normalized and validated by {@link TagRules}.
 */
public record TaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        TaskStatus status,

        LocalDate dueDate,

        @Size(max = TagRules.MAX_TAGS, message = "a task may have at most 5 tags")
        List<@Size(max = TagRules.MAX_LENGTH, message = "tags must be at most 30 characters") String> tags
) {
}
