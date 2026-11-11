package com.dsk.tasks;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * The only sort orders the API accepts. A token is mapped to a fixed
 * {@link Sort}; nothing from the request reaches a property name, an
 * expression or a second order, so {@code sort} cannot inject any of them.
 *
 * <p>Every order ends with {@code id}, so rows with equal keys keep a stable
 * position across pages: without it, a duplicate title could appear on two
 * pages while another row is skipped.
 */
final class TaskSort {

    static final String DEFAULT = "newest";

    private static final Map<String, Sort> ORDERS = Map.of(
            DEFAULT, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            "title", Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id")),
            "-title", Sort.by(Sort.Order.desc("title").ignoreCase(), Sort.Order.asc("id")),
            "dueDate", Sort.by(Sort.Order.asc("dueDate").nullsLast(), Sort.Order.asc("id")),
            "-dueDate", Sort.by(Sort.Order.desc("dueDate").nullsLast(), Sort.Order.asc("id")));

    private TaskSort() {
    }

    static Sort parse(String token) {
        Sort order = ORDERS.get(token == null ? "" : token.strip());
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sort must be one of " + String.join(", ", ORDERS.keySet().stream().sorted().toList()));
        }
        return order;
    }
}
