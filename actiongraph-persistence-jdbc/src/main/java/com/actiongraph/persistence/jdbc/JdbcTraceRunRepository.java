package com.actiongraph.persistence.jdbc;

import com.actiongraph.trace.TraceChainVerifier;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcTraceRunRepository {
    private final DataSource dataSource;
    private final String table;
    private final JdbcTraceRepository traceRepository;
    private final TraceChainVerifier traceChainVerifier = new TraceChainVerifier();

    public JdbcTraceRunRepository(DataSource dataSource) {
        this(dataSource, JdbcTraceRepository.DEFAULT_TABLE);
    }

    public JdbcTraceRunRepository(DataSource dataSource, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = JdbcTraceRepository.validateIdentifier(table);
        this.traceRepository = new JdbcTraceRepository(dataSource, table);
    }

    public List<TraceRunSummary> findRecentRuns(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String sql = "select run_id from " + table + " group by run_id order by max(at) desc";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TraceRunSummary> summaries = new ArrayList<>();
                while (resultSet.next()) {
                    findRun(resultSet.getString("run_id")).ifPresent(summaries::add);
                }
                return List.copyOf(summaries);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot query recent trace runs", ex);
        }
    }

    public Optional<TraceRunSummary> findRun(String runId) {
        Objects.requireNonNull(runId, "runId");
        List<TraceEvent> events = traceRepository.findByRun(runId);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(summary(events));
    }

    private TraceRunSummary summary(List<TraceEvent> events) {
        TraceEvent first = events.getFirst();
        TraceEvent last = events.getLast();
        TraceChainVerifier.ChainVerification verification = traceChainVerifier.verify(events);
        return new TraceRunSummary(
                first.runId(),
                first.at(),
                last.at(),
                status(events),
                events.size(),
                verification.valid(),
                verification.firstBrokenSeq(),
                verification.message()
        );
    }

    private String status(List<TraceEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            TraceEvent event = events.get(i);
            if (event.type() == TraceEventType.RUN_ENDED || event.type() == TraceEventType.RUN_SUSPENDED) {
                return event.data().getOrDefault("status", event.type().name());
            }
        }
        return "UNKNOWN";
    }
}
