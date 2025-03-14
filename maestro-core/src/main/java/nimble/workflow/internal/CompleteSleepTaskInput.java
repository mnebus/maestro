package nimble.workflow.internal;

import java.io.Serializable;

public record CompleteSleepTaskInput(
        String workflowId,
        String sleepIdentifier
) implements Serializable {
}
