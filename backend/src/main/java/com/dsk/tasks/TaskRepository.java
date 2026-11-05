package com.dsk.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Active rows only. The restriction is named rather than hidden behind
     * {@code @SQLRestriction}, because the archive view needs the same listing
     * code over the opposite set.
     */
    Optional<Task> findByIdAndArchivedAtIsNull(Long id);
}
