package nimble.workflow.internal;

import java.time.Duration;
import java.time.Instant;

public class WorkflowStillSleepingException extends WorkflowSleepingException {
    private Duration elapsedSleepTime;
    public WorkflowStillSleepingException(String identifier, Duration elapsedSleepTime, Duration napTime) {
        super(identifier, napTime);
        this.elapsedSleepTime = elapsedSleepTime;
    }

    public Duration getElapsedSleepTime() {
        return elapsedSleepTime;
    }
}
