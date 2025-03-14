package nimble.workflow.model;

public record Condition(
        String workflowId,
        String identifier,
        WorkflowEvent waiting,
        WorkflowEvent satisfied
) {
    public boolean isSatisfied() {
        return this.satisfied != null && this.satisfied.timestamp() != null;
    }

}
