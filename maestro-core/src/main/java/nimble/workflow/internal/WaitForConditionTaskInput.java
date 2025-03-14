package nimble.workflow.internal;

import java.io.Serializable;
import java.time.Duration;

public record WaitForConditionTaskInput(
        String workflowClassName,
        String workflowId
) implements Serializable {
}
