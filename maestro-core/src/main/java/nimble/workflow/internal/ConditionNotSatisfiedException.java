package nimble.workflow.internal;

public class ConditionNotSatisfiedException extends RuntimeException {
    private final String identifier;

    public ConditionNotSatisfiedException(String identifier) {
        super();
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
