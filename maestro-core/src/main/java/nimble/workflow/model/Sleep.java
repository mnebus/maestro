package nimble.workflow.model;

import java.time.Duration;

public record Sleep(
        String workflowId,
        String identifier,
        Duration duration,
        WorkflowEvent started,
        WorkflowEvent completed
) {

    public boolean isCompleted() {
        return completed != null && completed.timestamp() != null;
    }

}
