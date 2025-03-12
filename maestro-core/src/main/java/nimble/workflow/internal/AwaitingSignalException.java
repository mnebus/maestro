package nimble.workflow.internal;

public class AwaitingSignalException extends RuntimeException {

    private final String signal;

    public AwaitingSignalException(String signal) {
        super("Workflow execution aborted to wait for signal [%s]".formatted(signal));
        this.signal = signal;
    }

    public String getSignal() {
        return this.signal;
    }
}
