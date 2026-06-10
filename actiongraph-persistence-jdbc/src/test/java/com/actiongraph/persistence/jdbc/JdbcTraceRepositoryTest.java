package com.actiongraph.persistence.jdbc;

import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTraceRepositoryTest {
    @Test
    void persistsTraceEventsInSequenceOrder() {
        JdbcTraceRepository repository = new JdbcTraceRepository(JdbcTestDataSources.h2());
        repository.append(new TraceEvent(
                "RUN-1",
                2,
                Instant.parse("2026-01-01T00:00:02Z"),
                TraceEventType.ACTION_SUCCEEDED,
                "action.two",
                "second",
                Map.of("k2", "v2")
        ));
        repository.append(new TraceEvent(
                "RUN-1",
                1,
                Instant.parse("2026-01-01T00:00:01Z"),
                TraceEventType.RUN_STARTED,
                null,
                "first",
                Map.of("k1", "v1")
        ));

        assertThat(repository.findByRun("RUN-1"))
                .extracting(TraceEvent::seq)
                .containsExactly(1L, 2L);
        assertThat(repository.findByRun("RUN-1").get(1))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo(TraceEventType.ACTION_SUCCEEDED);
                    assertThat(event.actionId()).isEqualTo("action.two");
                    assertThat(event.data()).containsEntry("k2", "v2");
                });
    }

    @Test
    void rejectsUnsafeTableNames() {
        assertThatThrownBy(() -> new JdbcTraceRepository(JdbcTestDataSources.h2(), "trace;drop table x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL identifier");
    }

    @Test
    void appendsTraceEventsInBatch() {
        JdbcTraceRepository repository = new JdbcTraceRepository(JdbcTestDataSources.h2());

        repository.appendAll(List.of(
                new TraceEvent(
                        "RUN-BATCH",
                        1,
                        Instant.parse("2026-01-01T00:00:01Z"),
                        TraceEventType.RUN_STARTED,
                        null,
                        "first",
                        Map.of()
                ),
                new TraceEvent(
                        "RUN-BATCH",
                        2,
                        Instant.parse("2026-01-01T00:00:02Z"),
                        TraceEventType.RUN_ENDED,
                        null,
                        "done",
                        Map.of("status", "COMPLETED")
                )
        ));

        assertThat(repository.findByRun("RUN-BATCH"))
                .extracting(TraceEvent::type)
                .containsExactly(TraceEventType.RUN_STARTED, TraceEventType.RUN_ENDED);
    }
}
