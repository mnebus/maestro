package nimble.workflow.model;

import java.time.Instant;

public record WorkflowEvent(String category, String status, Instant timestamp) {
}
