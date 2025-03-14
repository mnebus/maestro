package nimble.workflow.internal;

public class AwaitingSignalException extends RuntimeException {
    private final String signal;

    public AwaitingSignalException(String signal) {
        super();
        this.signal = signal;
    }

    public String getSignal() {
        return this.signal;
    }
}
