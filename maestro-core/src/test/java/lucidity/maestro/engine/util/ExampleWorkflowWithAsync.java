package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.activity.Activity;
import lucidity.maestro.engine.api.async.Async;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;

import java.util.concurrent.CompletableFuture;

public class ExampleWorkflowWithAsync implements RunnableWorkflow<String, Integer> {

    @Activity
    ExampleAsyncActivity asyncActivity;

    @Override
    public String execute(Integer param) {
        System.out.println("Running ExampleWorkflowWithAsyncImpl with param [%s]".formatted(param));
        CompletableFuture<String> twoSecondFuture = Async.function(() -> asyncActivity.workFor2SecondsAndEcho("2-second-echo"));
        CompletableFuture<String> oneSecondFuture = Async.function(() -> asyncActivity.workFor1SecondAndEcho("1-second-echo"));

        try {
            String oneSecondEcho = oneSecondFuture.get();
            String twoSecondEcho = twoSecondFuture.get();

            return "param: [%s] oneSecondEcho: [%s] twoSecondEcho: [%s]".formatted(param, oneSecondEcho, twoSecondEcho);
        } catch (Exception e) {
            throw new RuntimeException(e.toString(), e);
        }

    }
}
