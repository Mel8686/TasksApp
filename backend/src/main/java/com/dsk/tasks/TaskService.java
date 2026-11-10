package com.dsk.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The use-case boundary: one transaction per operation, and the tags a response
 * needs are read before it ends. The controller keeps HTTP concerns (status
 * codes, ETags) and nothing else.
 */
@Service
public class TaskService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TaskRepository tasks;
    private final TagService tags;

    public TaskService(TaskRepository tasks, TagService tags) {
        this.tasks = tasks;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public Page<Task> list(int page, int size, String q, TaskStatus status, String tag, boolean archived) {
        Page<Task> found = tasks.findAll(filter(q, status, tag, archived),
                PageRequest.of(page, size, NEWEST_FIRST));
        loadTags(found.getContent());
        return found;
    }

    /** One page of the export stream; same filter, no count needed. */
    @Transactional(readOnly = true)
    public List<Task> batch(int page, int size, String q, TaskStatus status, String tag, boolean archived) {
        List<Task> rows = tasks.findAll(filter(q, status, tag, archived),
                PageRequest.of(page, size, NEWEST_FIRST)).getContent();
        loadTags(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public Task get(Long id) {
        Task task = tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id));
        loadTags(List.of(task));
        return task;
    }

    @Transactional
    public Task create(TaskRequest request) {
        Task task = new Task();
        apply(task, request);
        return tasks.save(task);
    }

    /**
     * @param expectedVersion the version the client read, or null for
     *                        {@code If-Match: *}, which skips the check
     */
    @Transactional
    public Task update(Long id, Long expectedVersion, TaskRequest changes) {
        Task task = tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id));
        // The 409/412 body carries this row, so its tags must be loaded too.
        loadTags(List.of(task));

        if (expectedVersion != null && expectedVersion != task.getVersion()) {
            throw new TaskConflictException(task);
        }
        apply(task, changes);
        return task;
    }

    @Transactional
    public void archive(Long id) {
        Task task = tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id));
        task.archive();
    }

    @Transactional
    public Task restore(Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> notFound(id));
        if (task.getArchivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task " + id + " is not archived");
        }
        loadTags(List.of(task));
        task.restore();
        return task;
    }

    /**
     * One transaction for the whole batch: a failed id rolls back every row, so
     * a caller never gets a half-applied selection.
     *
     * @return how many rows changed
     */
    @Transactional
    public int bulk(BulkRequest request) {
        if (request.operation() == BulkOperation.STATUS && request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status is required for the STATUS operation");
        }

        List<Long> ids = request.ids().stream().distinct().toList();
        List<Task> found = tasks.findAllById(ids);
        if (found.size() != ids.size()) {
            List<Long> missing = new ArrayList<>(ids);
            found.forEach(task -> missing.remove(task.getId()));
            throw notFound(missing.get(0));
        }

        for (Task task : found) {
            boolean archived = task.getArchivedAt() != null;
            switch (request.operation()) {
                case ARCHIVE -> {
                    if (archived) {
                        throw notFound(task.getId());
                    }
                    task.archive();
                }
                case STATUS -> {
                    if (archived) {
                        throw notFound(task.getId());
                    }
                    task.setStatus(request.status());
                }
                case RESTORE -> {
                    if (!archived) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Task " + task.getId() + " is not archived");
                    }
                    task.restore();
                }
            }
        }
        return found.size();
    }

    private void apply(Task task, TaskRequest request) {
        task.setTitle(request.title().strip());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        task.setDueDate(request.dueDate());
        try {
            task.setTags(tags.resolve(request.tags()));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * The session closes before Jackson runs ({@code open-in-view=false}), so a
     * lazy collection would blow up mid-serialization. Touching it here loads
     * the whole page in one extra query, thanks to {@code @BatchSize} on the
     * association.
     */
    private static void loadTags(List<Task> rows) {
        for (Task task : rows) {
            task.getTags().size();
        }
    }

    private static Specification<Task> filter(String q, TaskStatus status, String tag, boolean archived) {
        List<Specification<Task>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> archived
                ? cb.isNotNull(root.get("archivedAt"))
                : cb.isNull(root.get("archivedAt")));
        String term = q.strip();
        if (!term.isEmpty()) {
            String pattern = likePattern(term);
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        String name = tag.strip().toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) {
            filters.add((root, query, cb) -> cb.equal(root.join("tags").get("name"), name));
        }
        return filters.stream().reduce(Specification::and).orElse(null);
    }

    /**
     * Wildcards in user input are escaped, so a search for {@code 100%} matches
     * that text rather than every row.
     */
    private static String likePattern(String term) {
        String escaped = term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No task " + id);
    }
}
