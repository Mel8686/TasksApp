package com.dsk.tasks;

/**
 * Closed set of task states. Anything outside this enum is rejected at the
 * JSON boundary, so the API cannot store a state the UI cannot render.
 */
public enum TaskStatus {
    TODO,
    DOING,
    DONE
}
