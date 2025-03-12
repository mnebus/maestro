package nimble.workflow.model;

import java.io.Serializable;

public record Activity(
        String workflowId,
        String name,
        Serializable output,
        WorkflowEvent started,
        WorkflowEvent completed
) {
}
