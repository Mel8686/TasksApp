package com.dsk.tasks;

public enum BulkOperation {
    /** Active rows to the archive. An already-archived id is a 404, as with DELETE. */
    ARCHIVE,
    /** Archived rows back to the list. An active id is a 409, as with restore. */
    RESTORE,
    /** Set {@code status} on active rows. */
    STATUS
}
