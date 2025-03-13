package nimble.workflow.util;

import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import nimble.workflow.api.WorkflowFunctions;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class ExampleWorkflow implements RunnableWorkflow<String, Integer> {

    private final ExampleService service;

    private static AtomicInteger conditionCheckCount = new AtomicInteger(0);

    public ExampleWorkflow(ExampleService service) {

        this.service = service;
    }

    @Override
    public String execute(Integer param) {

        String convertedToString = WorkflowFunctions.activity("Convert to String",
                () -> param.toString());

        boolean okToResume = WorkflowFunctions.awaitSignal("OkToResume", Boolean.class);
        System.out.println("received signal to resume [%s]".formatted(okToResume));

        WorkflowFunctions.activity("Work for between 1 and 5 seconds", service::doSomeWork);

        String concatenated = WorkflowFunctions.activity("Concatenate",
                () -> convertedToString + "asdf");

        WorkflowFunctions.sleep("take a nap", Duration.ofSeconds(1));

        CompletableFuture<Void> sleepFor10 = WorkflowFunctions.async(()
                -> WorkflowFunctions.activity("Work for 10 seconds", () -> sleepForMillis(10_000)));

        CompletableFuture<Void> sleepFor1 = WorkflowFunctions.async(()
                -> WorkflowFunctions.activity("Work for 1 second", () -> sleepForMillis(1_000)));

        WorkflowFunctions.awaitCondition("run a few times", () -> {
            if (conditionCheckCount.getAndIncrement() > 2) {
                return true;
            }
            return false;
        });
        try {
            sleepFor10.get();
            sleepFor1.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return concatenated;
    }

    static void sleepForMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
