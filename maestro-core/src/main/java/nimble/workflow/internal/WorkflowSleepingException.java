package nimble.workflow.internal;

import java.time.Duration;

public class WorkflowSleepingException extends RuntimeException {
    private final String identifier;
    private final Duration napTime;

    public WorkflowSleepingException(String identifier, Duration napTime) {
        super();
        this.identifier = identifier;
        this.napTime = napTime;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Duration getNapTime() {
        return napTime;
    }
}
