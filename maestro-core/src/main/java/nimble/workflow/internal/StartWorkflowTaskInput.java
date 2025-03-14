package nimble.workflow.internal;

import java.io.Serializable;

public record StartWorkflowTaskInput(String workflowId) implements Serializable {
}
