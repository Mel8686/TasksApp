package com.dsk.tasks;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 reader/writer: quoted fields, doubled quotes, CRLF or LF,
 * embedded newlines. Deliberately no dependency for a format this small.
 */
final class Csv {

    private Csv() {
    }

    static List<List<String>> parse(String input) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    static String escape(String value) {
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * A cell starting with {@code = + - @} (or a tab/CR) is executed as a
     * formula by spreadsheets. Prefixing an apostrophe keeps the text literal.
     */
    static String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return isFormulaLead(value.charAt(0)) ? "'" + value : value;
    }

    /** Undo {@link #neutralizeFormula} on import so a round trip is lossless. */
    static String restoreFormula(String value) {
        if (value.length() > 1 && value.charAt(0) == '\'' && isFormulaLead(value.charAt(1))) {
            return value.substring(1);
        }
        return value;
    }

    private static boolean isFormulaLead(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}
