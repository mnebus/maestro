package nimble.workflow.internal;

import java.io.Serializable;

public record StartWorkflowTaskInput(
        Class workflowClass,
        Object workflowInput,
        String workflowId) implements Serializable {
}
