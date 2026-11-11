package com.dsk.tasks;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final int EXPORT_BATCH = 500;

    private final TaskService service;
    private final TaskImportService imports;

    public TaskController(TaskService service, TaskImportService imports) {
        this.service = service;
        this.imports = imports;
    }

    public record ImportResult(int imported) {
    }

    public record BulkResult(int updated) {
    }

    /**
     * {@code sort} is a token from a whitelist, never a field name: see
     * {@link TaskSort}. An unknown token is a 400, not a 500.
     */
    @GetMapping
    public PageResponse<Task> list(
            @RequestParam(defaultValue = "") @Size(max = 100, message = "q must be at most 100 characters") String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "") @Size(max = 30, message = "tag must be at most 30 characters") String tag,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = TaskSort.DEFAULT) String sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative")
            @Max(value = 10_000, message = "page must be at most 10000") int page,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 50, message = "size must be at most 50") int size) {

        return PageResponse.of(service.list(page, size, q, status, tag, archived, sort));
    }

    /**
     * Full extract of the current filter, not a page. Rows are fetched in
     * batches of {@value #EXPORT_BATCH} and written as they arrive, so memory
     * stays flat while the response streams.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public void export(
            @RequestParam(defaultValue = "") @Size(max = 100, message = "q must be at most 100 characters") String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "") @Size(max = 30, message = "tag must be at most 30 characters") String tag,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = TaskSort.DEFAULT) String sort,
            HttpServletResponse response) throws IOException {

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks.csv\"");

        Writer out = response.getWriter();
        out.write("id,title,status,dueDate,createdAt,archivedAt,tags\r\n");
        for (int page = 0; ; page++) {
            List<Task> rows = service.batch(page, EXPORT_BATCH, q, status, tag, archived, sort);
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
                out.write(',');
                out.write(Csv.escape(Csv.neutralizeFormula(tagCell(task))));
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
        return withVersion(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody TaskRequest request) {
        return service.create(request);
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

        return withVersion(service.update(id, parseIfMatch(ifMatch), changes));
    }

    /**
     * Title-only partial update for inline editing. Everything else on the row
     * is untouched, and it takes {@code If-Match} exactly like {@code PUT}: a
     * rename is still a write against a version the client read.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Task> rename(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody TaskTitlePatch patch) {

        return withVersion(service.rename(id, parseIfMatch(ifMatch), patch.title()));
    }

    /**
     * Soft delete: the row stays and moves to the archive. Idempotent in the
     * weak sense that archiving twice returns 404, not a second archive.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archive(id);
    }

    /** Bring an archived task back. 409 when the row is already active. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<Task> restore(@PathVariable Long id) {
        return withVersion(service.restore(id));
    }

    /** Archive, restore or set the status of up to 100 rows in one transaction. */
    @PostMapping("/bulk")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return new BulkResult(service.bulk(request));
    }

    private static ResponseEntity<Task> withVersion(Task task) {
        return ResponseEntity.ok().eTag("\"" + task.getVersion() + "\"").body(task);
    }

    /** Tags travel as one cell, {@code a|b}, in name order. */
    private static String tagCell(Task task) {
        return task.getTags().stream().map(Tag::getName).collect(Collectors.joining(TagRules.SEPARATOR));
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
}
