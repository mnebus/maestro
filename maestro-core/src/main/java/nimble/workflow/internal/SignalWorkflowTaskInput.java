package nimble.workflow.internal;

import java.io.Serializable;

public record SignalWorkflowTaskInput(
        String workflowId,
        String signalName,
        Serializable signalValue
) implements Serializable {
}
