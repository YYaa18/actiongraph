package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleRunPage;
import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.ConsoleRunSummary;
import com.actiongraph.trace.TraceEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphConsoleServiceAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphConsoleServiceAutoConfiguration.class));

    @Test
    void consoleServiceIsDisabledByDefault() {
        contextRunner
                .withBean(ConsoleRunRepository.class, EmptyConsoleRunRepository::new)
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsoleService.class));
    }

    @Test
    void consoleServiceIsNotCreatedWithoutRepository() {
        contextRunner
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsoleService.class));
    }

    @Test
    void createsConsoleServiceFromRepositoryAndProperties() {
        contextRunner
                .withBean(ConsoleRunRepository.class, EmptyConsoleRunRepository::new)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.default-limit=7",
                        "actiongraph.console.max-limit=11"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleService.class);
                    assertThat(context.getBean(ActionGraphConsoleService.class)
                            .renderPage("__ACTIONGRAPH_CONSOLE_TOKEN_HEADER__ "
                                    + "__ACTIONGRAPH_CONSOLE_DEFAULT_LIMIT__ "
                                    + "__ACTIONGRAPH_CONSOLE_MAX_LIMIT__"))
                            .contains("'X-Internal-Console-Token' 7 11");
                });
    }

    private static final class EmptyConsoleRunRepository implements ConsoleRunRepository {
        @Override
        public ConsoleRunPage findRuns(ConsoleRunQuery query) {
            return new ConsoleRunPage(query.limit(), query.offset(), 0, List.of());
        }

        @Override
        public Optional<ConsoleRunSummary> findRun(String runId) {
            return Optional.empty();
        }

        @Override
        public List<TraceEvent> findTraceEvents(String runId) {
            return List.of();
        }
    }
}
