package com.dsk.tasks;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TaskImportService {

    static final long MAX_BYTES = 1_048_576;
    static final int MAX_ROWS = 1_000;
    private static final int MAX_REPORTED = 20;

    private final TaskRepository tasks;
    private final TagService tags;

    public TaskImportService(TaskRepository tasks, TagService tags) {
        this.tasks = tasks;
        this.tags = tags;
    }

    /**
     * All-or-nothing: every row is validated first, and the whole batch is
     * rejected if any row is invalid. Runs in one transaction.
     */
    @Transactional
    public int importCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        List<List<String>> rows = Csv.parse(text);
        if (rows.isEmpty()) {
            throw new ImportRejectedException("CSV is empty");
        }

        List<String> header = rows.get(0).stream()
                .map(cell -> cell.strip().toLowerCase(Locale.ROOT))
                .toList();
        int titleIndex = header.indexOf("title");
        if (titleIndex < 0) {
            throw new ImportRejectedException("CSV header must include a title column");
        }
        int statusIndex = header.indexOf("status");
        int dueDateIndex = header.indexOf("duedate");
        int tagsIndex = header.indexOf("tags");

        List<List<String>> data = rows.subList(1, rows.size());
        if (data.size() > MAX_ROWS) {
            throw new ImportRejectedException("CSV may contain at most " + MAX_ROWS + " rows");
        }

        List<Task> accepted = new ArrayList<>();
        // Kept beside `accepted` so tags can be resolved for the whole file at once.
        List<List<String>> acceptedTags = new ArrayList<>();
        List<ImportRejectedException.Rejection> reported = new ArrayList<>();
        int rejected = 0;

        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = i + 2; // header is line 1

            List<String> problems = new ArrayList<>();
            Task task = new Task();

            String title = Csv.restoreFormula(cell(row, titleIndex).strip());
            if (title.isEmpty()) {
                problems.add("title must not be blank");
            } else if (title.length() > 200) {
                problems.add("title must be at most 200 characters");
            } else {
                task.setTitle(title);
            }

            String status = cell(row, statusIndex).strip();
            if (status.isEmpty()) {
                task.setStatus(TaskStatus.TODO);
            } else {
                try {
                    task.setStatus(TaskStatus.valueOf(status.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    problems.add("status must be TODO, DOING or DONE");
                }
            }

            String dueDate = cell(row, dueDateIndex).strip();
            if (!dueDate.isEmpty()) {
                try {
                    task.setDueDate(LocalDate.parse(dueDate));
                } catch (DateTimeParseException ex) {
                    problems.add("dueDate must be yyyy-MM-dd");
                }
            }

            // archivedAt is deliberately ignored: an import creates active rows.
            List<String> tagNames = List.of();
            try {
                tagNames = TagRules.normalize(TagRules.fromCell(Csv.restoreFormula(cell(row, tagsIndex))));
            } catch (IllegalArgumentException ex) {
                problems.add(ex.getMessage());
            }

            if (problems.isEmpty()) {
                accepted.add(task);
                acceptedTags.add(tagNames);
            } else {
                rejected++;
                if (reported.size() < MAX_REPORTED) {
                    reported.add(new ImportRejectedException.Rejection(line, String.join("; ", problems)));
                }
            }
        }

        if (rejected > 0) {
            throw new ImportRejectedException(
                    rejected + (rejected == 1 ? " row was rejected" : " rows were rejected"),
                    rejected,
                    reported);
        }

        linkTags(accepted, acceptedTags);
        tasks.saveAll(accepted);
        return accepted.size();
    }

    /** One lookup for the whole file, then the join rows fall out of the mapping. */
    private void linkTags(List<Task> accepted, List<List<String>> acceptedTags) {
        Set<String> names = new LinkedHashSet<>();
        acceptedTags.forEach(names::addAll);
        Map<String, Tag> resolved = tags.resolveAll(names);

        for (int i = 0; i < accepted.size(); i++) {
            Set<Tag> taskTags = new LinkedHashSet<>();
            for (String name : acceptedTags.get(i)) {
                taskTags.add(resolved.get(name));
            }
            accepted.get(i).setTags(taskTags);
        }
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }
}
