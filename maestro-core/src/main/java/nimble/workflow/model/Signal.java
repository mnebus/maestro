package nimble.workflow.model;

public record Signal(
        String workflowId,
        String name,
        Object value,
        WorkflowEvent waiting,
        WorkflowEvent received
) {
    public boolean isReceived() {
        return this.received != null && this.received.timestamp() != null;
    }
}
