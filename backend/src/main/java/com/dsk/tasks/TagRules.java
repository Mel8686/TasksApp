package com.dsk.tasks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The tag vocabulary rules, in one place so the API, the CSV import and the
 * database column agree: at most {@value #MAX_TAGS} per task, at most
 * {@value #MAX_LENGTH} characters each, lowercase, no {@value #SEPARATOR}
 * (it separates tags inside one CSV cell).
 */
final class TagRules {

    static final int MAX_TAGS = 5;
    static final int MAX_LENGTH = 30;
    static final String SEPARATOR = "|";

    private TagRules() {
    }

    /**
     * Trim, lowercase and de-duplicate. Rejects blank, over-long and
     * separator-bearing names, and more than {@value #MAX_TAGS} distinct names.
     *
     * @throws IllegalArgumentException with a message safe to show the caller
     */
    static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : raw) {
            String name = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("tags must not be blank");
            }
            if (name.contains(SEPARATOR)) {
                throw new IllegalArgumentException("tags must not contain '" + SEPARATOR + "'");
            }
            if (name.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "tags must be at most " + MAX_LENGTH + " characters");
            }
            names.add(name);
        }
        if (names.size() > MAX_TAGS) {
            throw new IllegalArgumentException("a task may have at most " + MAX_TAGS + " tags");
        }
        return List.copyOf(names);
    }

    /** The cells of one import column, split on the separator and not yet normalized. */
    static List<String> fromCell(String cell) {
        if (cell.isBlank()) {
            return List.of();
        }
        // -1 keeps trailing blanks, so "a|" is rejected rather than silently trimmed.
        return new ArrayList<>(List.of(cell.split("\\|", -1)));
    }
}
