package com.dsk.tasks;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TaskImportService {

    static final long MAX_BYTES = 1_048_576;
    static final int MAX_ROWS = 1_000;
    private static final int MAX_REPORTED = 20;

    private final TaskRepository tasks;

    public TaskImportService(TaskRepository tasks) {
        this.tasks = tasks;
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

        List<List<String>> data = rows.subList(1, rows.size());
        if (data.size() > MAX_ROWS) {
            throw new ImportRejectedException("CSV may contain at most " + MAX_ROWS + " rows");
        }

        List<Task> accepted = new ArrayList<>();
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

            if (problems.isEmpty()) {
                accepted.add(task);
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

        tasks.saveAll(accepted);
        return accepted.size();
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }
}
