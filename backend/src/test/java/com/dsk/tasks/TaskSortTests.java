package com.dsk.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskSortTests {

    private static final String[] TOKENS = {"newest", "title", "-title", "dueDate", "-dueDate"};

    @Test
    void unknownTokenIsABadRequestNotAProperty() {
        assertThatThrownBy(() -> TaskSort.parse("title;drop table task"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void everyOrderEndsWithIdSoPagingIsStable() {
        for (String token : TOKENS) {
            Sort sort = TaskSort.parse(token);
            assertThat(sort.getOrderFor("id"))
                    .as("tie-breaker for %s", token)
                    .isNotNull();
        }
    }

    @Test
    void dueDateOrdersKeepEmptyDatesAtTheEnd() {
        for (String token : new String[]{"dueDate", "-dueDate"}) {
            Sort sort = TaskSort.parse(token);
            assertThat(sort.getOrderFor("dueDate").getNullHandling())
                    .as(token)
                    .isEqualTo(Sort.NullHandling.NULLS_LAST);
        }
    }

    @Test
    void defaultIsStillNewestFirst() {
        Sort sort = TaskSort.parse(TaskSort.DEFAULT);
        assertThat(sort.getOrderFor("createdAt").isDescending()).isTrue();
    }
}
