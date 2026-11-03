package com.dsk.tasks;

/**
 * Raised when a write carries an {@code If-Match} version that no longer
 * matches the stored row. Carries the current row so the client can show what
 * it would have overwritten instead of guessing.
 */
public class TaskConflictException extends RuntimeException {

    private final transient Task current;

    public TaskConflictException(Task current) {
        super("Task " + current.getId() + " was modified by someone else");
        this.current = current;
    }

    public Task getCurrent() {
        return current;
    }
}
