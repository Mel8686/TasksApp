package com.dsk.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/tasks/{id}}: the title and nothing else. Inline
 * editing has no business sending status, dates or tags — a full replacement is
 * {@code PUT}'s job — so a patched row can only change where this record points.
 */
public record TaskTitlePatch(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title) {
}
