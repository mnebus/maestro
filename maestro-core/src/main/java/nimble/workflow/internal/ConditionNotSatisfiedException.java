package nimble.workflow.internal;

public class ConditionNotSatisfiedException extends RuntimeException {
    public ConditionNotSatisfiedException(String msg) {
        super(msg);
    }
}
