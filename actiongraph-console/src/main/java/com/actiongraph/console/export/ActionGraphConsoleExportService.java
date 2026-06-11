package com.actiongraph.console.export;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleRunSummaryResponse;
import com.actiongraph.console.ConsoleRunsResponse;
import com.actiongraph.console.ConsoleTraceEventResponse;
import com.actiongraph.console.ConsoleTraceResponse;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public final class ActionGraphConsoleExportService {
    private final ActionGraphConsoleService consoleService;

    public ActionGraphConsoleExportService(ActionGraphConsoleService consoleService) {
        this.consoleService = Objects.requireNonNull(consoleService, "consoleService");
    }

    public String runsCsv(
            @Nullable Integer limit,
            @Nullable Integer offset,
            @Nullable String status,
            @Nullable Boolean auditComplete
    ) {
        ConsoleRunsResponse response = consoleService.recentRuns(limit, offset, status, auditComplete);
        StringBuilder csv = new StringBuilder();
        appendRow(csv,
                "runId",
                "firstEventAt",
                "lastEventAt",
                "status",
                "traceEventCount",
                "auditComplete",
                "firstBrokenSeq",
                "auditMessage"
        );
        for (ConsoleRunSummaryResponse run : response.runs()) {
            appendRow(csv,
                    run.runId(),
                    string(run.firstEventAt()),
                    string(run.lastEventAt()),
                    run.status(),
                    Integer.toString(run.traceEventCount()),
                    Boolean.toString(run.auditComplete()),
                    Long.toString(run.firstBrokenSeq()),
                    run.auditMessage()
            );
        }
        return csv.toString();
    }

    public String traceCsv(String runId) {
        ConsoleTraceResponse response = consoleService.trace(runId);
        StringBuilder csv = new StringBuilder();
        appendRow(csv, "runId", "seq", "at", "type", "actionId", "detail", "data", "prevHash", "hash");
        for (ConsoleTraceEventResponse event : response.events()) {
            appendRow(csv,
                    response.runId(),
                    Long.toString(event.seq()),
                    string(event.at()),
                    event.type(),
                    event.actionId(),
                    event.detail(),
                    jsonObject(event.data()),
                    event.prevHash(),
                    event.hash()
            );
        }
        return csv.toString();
    }

    public String traceJsonl(String runId) {
        ConsoleTraceResponse response = consoleService.trace(runId);
        StringBuilder jsonl = new StringBuilder();
        for (ConsoleTraceEventResponse event : response.events()) {
            jsonl.append('{')
                    .append(jsonField("runId", response.runId())).append(',')
                    .append(jsonField("seq", event.seq())).append(',')
                    .append(jsonField("at", string(event.at()))).append(',')
                    .append(jsonField("type", event.type())).append(',')
                    .append(jsonField("actionId", event.actionId())).append(',')
                    .append(jsonField("detail", event.detail())).append(',')
                    .append("\"data\":").append(jsonObject(event.data())).append(',')
                    .append(jsonField("prevHash", event.prevHash())).append(',')
                    .append(jsonField("hash", event.hash()))
                    .append('}')
                    .append('\n');
        }
        return jsonl.toString();
    }

    private static String string(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    private static void appendRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvValue(values[i]));
        }
        csv.append('\n');
    }

    private static String csvValue(@Nullable String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String jsonField(String name, String value) {
        return jsonString(name) + ":" + jsonString(value);
    }

    private static String jsonField(String name, long value) {
        return jsonString(name) + ":" + value;
    }

    private static String jsonObject(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            if (!first) {
                json.append(',');
            }
            json.append(jsonString(entry.getKey())).append(':').append(jsonString(entry.getValue()));
            first = false;
        }
        return json.append('}').toString();
    }

    private static String jsonString(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        json.append(String.format("\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
