package com.dsk.tasks;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final int EXPORT_BATCH = 500;

    private final TaskRepository tasks;
    private final TaskImportService imports;

    public TaskController(TaskRepository tasks, TaskImportService imports) {
        this.tasks = tasks;
        this.imports = imports;
    }

    public record ImportResult(int imported) {
    }

    @GetMapping
    public PageResponse<Task> list(
            @RequestParam(defaultValue = "") @Size(max = 100, message = "q must be at most 100 characters") String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative")
            @Max(value = 10_000, message = "page must be at most 10000") int page,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 50, message = "size must be at most 50") int size) {

        PageRequest request = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponse.of(tasks.findAll(filter(q, status, archived), request));
    }

    /**
     * Full extract of the current filter, not a page. Rows are fetched in
     * batches of {@value #EXPORT_BATCH} and written as they arrive, so memory
     * stays flat while the response streams; the trade-off is one count query
     * per batch.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public void export(
            @RequestParam(defaultValue = "") @Size(max = 100, message = "q must be at most 100 characters") String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "false") boolean archived,
            HttpServletResponse response) throws IOException {

        Specification<Task> filter = filter(q, status, archived);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks.csv\"");

        Writer out = response.getWriter();
        out.write("id,title,status,dueDate,createdAt,archivedAt\r\n");
        for (int page = 0; ; page++) {
            PageRequest batch = PageRequest.of(page, EXPORT_BATCH, NEWEST_FIRST);
            List<Task> rows = tasks.findAll(filter, batch).getContent();
            for (Task task : rows) {
                out.write(task.getId().toString());
                out.write(',');
                out.write(Csv.escape(Csv.neutralizeFormula(task.getTitle())));
                out.write(',');
                out.write(task.getStatus().name());
                out.write(',');
                out.write(task.getDueDate() == null ? "" : task.getDueDate().toString());
                out.write(',');
                out.write(task.getCreatedAt().toString());
                out.write(',');
                out.write(task.getArchivedAt() == null ? "" : task.getArchivedAt().toString());
                out.write("\r\n");
            }
            if (rows.size() < EXPORT_BATCH) {
                break;
            }
        }
        out.flush();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        if (file.getSize() > TaskImportService.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV must be at most 1 MiB");
        }
        return new ImportResult(imports.importCsv(file.getBytes()));
    }

    /** Archived rows are invisible here: they only exist behind {@code ?archived=true}. */
    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable Long id) {
        return withVersion(tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody TaskRequest request) {
        Task task = new Task();
        task.setTitle(request.title().strip());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setDueDate(request.dueDate());
        return tasks.save(task);
    }

    /**
     * Requires {@code If-Match}: the version the client read, or {@code *} to
     * overwrite regardless. A mismatch is a lost update, so it is refused.
     * Checking here narrows the window; the {@code @Version} column on flush is
     * what actually closes it.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Task> update(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody TaskRequest changes) {

        // Existence first: a missing or archived id is a 404 whatever the header says.
        Task task = tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id));
        Long expected = parseIfMatch(ifMatch);

        if (expected != null && expected != task.getVersion()) {
            throw new TaskConflictException(task);
        }

        task.setTitle(changes.title().strip());
        if (changes.status() != null) {
            task.setStatus(changes.status());
        }
        task.setDueDate(changes.dueDate());
        return withVersion(tasks.save(task));
    }

    /**
     * Soft delete: the row stays and moves to the archive. Idempotent in the
     * weak sense that archiving twice returns 404, not a second archive.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Task task = tasks.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound(id));
        task.archive();
        tasks.save(task);
    }

    /** Bring an archived task back. 409 when the row is already active. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<Task> restore(@PathVariable Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> notFound(id));
        if (task.getArchivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task " + id + " is not archived");
        }
        task.restore();
        return withVersion(tasks.save(task));
    }

    private static Specification<Task> filter(String q, TaskStatus status, boolean archived) {
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

    private static ResponseEntity<Task> withVersion(Task task) {
        return ResponseEntity.ok().eTag("\"" + task.getVersion() + "\"").body(task);
    }

    /**
     * Returns the version the client expects, or {@code null} for
     * {@code If-Match: *}, which opts out of the check. Only the exact form we
     * emit is accepted: {@code If-Match} requires a strong comparison, so a
     * weak tag ({@code W/"1"}) is rejected rather than silently honoured.
     */
    private static Long parseIfMatch(String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "If-Match header is required; send the version from GET, or '*' to overwrite");
        }
        String tag = header.strip();
        if (tag.equals("*")) {
            return null;
        }
        if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
            tag = tag.substring(1, tag.length() - 1);
        }
        try {
            return Long.parseLong(tag);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "If-Match must be a version from GET, such as \"3\", or '*'");
        }
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No task " + id);
    }
}
