package com.actiongraph.console.jdbc;

import com.actiongraph.console.ConsoleRunPage;
import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.ConsoleRunSummary;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.persistence.jdbc.TraceRunPage;
import com.actiongraph.persistence.jdbc.TraceRunQuery;
import com.actiongraph.persistence.jdbc.TraceRunSummary;
import com.actiongraph.trace.TraceEvent;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcConsoleRunRepository implements ConsoleRunRepository {
    private final JdbcTraceRunRepository delegate;

    public JdbcConsoleRunRepository(DataSource dataSource) {
        this(dataSource, JdbcTraceRepository.DEFAULT_TABLE);
    }

    public JdbcConsoleRunRepository(DataSource dataSource, String table) {
        this(new JdbcTraceRunRepository(dataSource, table));
    }

    public JdbcConsoleRunRepository(JdbcTraceRunRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ConsoleRunPage findRuns(ConsoleRunQuery query) {
        Objects.requireNonNull(query, "query");
        TraceRunPage page = delegate.findRuns(new TraceRunQuery(
                query.limit(),
                query.offset(),
                query.status(),
                query.auditComplete()
        ));
        return new ConsoleRunPage(
                page.limit(),
                page.offset(),
                page.total(),
                page.runs().stream().map(JdbcConsoleRunRepository::from).toList()
        );
    }

    @Override
    public Optional<ConsoleRunSummary> findRun(String runId) {
        return delegate.findRun(runId).map(JdbcConsoleRunRepository::from);
    }

    @Override
    public List<TraceEvent> findTraceEvents(String runId) {
        return delegate.findTraceEvents(runId);
    }

    private static ConsoleRunSummary from(TraceRunSummary summary) {
        return new ConsoleRunSummary(
                summary.runId(),
                summary.firstEventAt(),
                summary.lastEventAt(),
                summary.status(),
                summary.traceEventCount(),
                summary.auditComplete(),
                summary.firstBrokenSeq(),
                summary.auditMessage()
        );
    }
}
