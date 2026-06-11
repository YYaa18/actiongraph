package com.actiongraph.exception;

import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.SuspendedRunNotClaimableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphExceptionTest {
    @Test
    void typedRuntimeExceptionsShareActionGraphBaseClasses() {
        MissingGoalParameterException missing = new MissingGoalParameterException("customerId");
        SuspendedRunNotClaimableException notClaimable = new SuspendedRunNotClaimableException("RUN-1");

        assertThat(missing)
                .isInstanceOf(ActionGraphException.class)
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessage("Missing required goal parameter: customerId");
        assertThat(missing.parameterName()).isEqualTo("customerId");

        assertThat(notClaimable)
                .isInstanceOf(ActionGraphException.class)
                .isInstanceOf(ActionGraphConflictException.class)
                .hasMessageContaining("RUN-1");
        assertThat(notClaimable.runId()).isEqualTo("RUN-1");
    }

    @Test
    void notFoundExceptionCarriesResourceIdentity() {
        ActionGraphNotFoundException exception = new ActionGraphNotFoundException(
                "trace run", "RUN-404", "Trace run not found: RUN-404");

        assertThat(exception)
                .isInstanceOf(ActionGraphException.class)
                .hasMessage("Trace run not found: RUN-404");
        assertThat(exception.resourceType()).isEqualTo("trace run");
        assertThat(exception.resourceId()).isEqualTo("RUN-404");
    }
}
