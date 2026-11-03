package com.dsk.tasks;

import java.util.List;

/**
 * Thrown when an import is rejected as a whole. Nothing is written: the service
 * method is transactional, so either every row is accepted or none is.
 */
public class ImportRejectedException extends RuntimeException {

    public record Rejection(int row, String message) {
    }

    private final int count;
    private final List<Rejection> rejections;

    public ImportRejectedException(String message) {
        this(message, 0, List.of());
    }

    public ImportRejectedException(String message, int count, List<Rejection> rejections) {
        super(message);
        this.count = count;
        this.rejections = List.copyOf(rejections);
    }

    public int getCount() {
        return count;
    }

    public List<Rejection> getRejections() {
        return rejections;
    }
}
