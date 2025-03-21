package lucidity.maestro.engine.internal.dto;

public record WorkflowContext(
        String workflowId,
        Long mutableCorrelationNumber,
        Long staticCorrelationNumber,
        Object workflow
) {

    public WorkflowContext incrementCorrelationNumber() {
        return new WorkflowContext(workflowId, mutableCorrelationNumber + 1, staticCorrelationNumber, workflow);
    }
}
