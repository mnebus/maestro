package nimble.workflow.api;

import com.github.kagkarlsson.scheduler.Scheduler;
import nimble.workflow.NimbleWorkflow;
import nimble.workflow.internal.AwaitingSignalException;
import nimble.workflow.internal.CompleteSleepTaskInput;
import nimble.workflow.internal.SchedulerConfig;
import nimble.workflow.internal.WorkflowSleepingException;
import nimble.workflow.model.Signal;
import nimble.workflow.model.Sleep;
import nimble.workflow.model.Workflow;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class WorkflowFunctions {

    private static WorkflowFunctions SINGLETON = null;
    private final Scheduler scheduler;

    public WorkflowFunctions(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public static void initialize(Scheduler scheduler) {
        SINGLETON = new WorkflowFunctions(scheduler);
    }

    public static void sleep(String identifier, Duration duration) {
        SINGLETON._sleep(identifier, duration);
    }

    public static <R extends Serializable> CompletableFuture<R> async(Supplier<R> supplier) {
        return SINGLETON._async(supplier);
    }

    public static CompletableFuture<Void> async(Runnable runnable) {
        return SINGLETON._async(runnable);
    }

    public static <R extends Serializable> R activity(String activityName, Supplier<R> supplier) {
        return SINGLETON._activity(activityName, supplier);
    }

    public static void activity(String activityName, Runnable runnable) {
        SINGLETON._activity(activityName, runnable);
    }

    public static <T> T awaitSignal(String signalName, Class<T> returnType) {
        return SINGLETON._awaitSignal(signalName, returnType);
    }

    private void _sleep(String identifier, Duration duration) {
        String workflowId = WorkflowExecutor.workflowId.get();
        Sleep sleep = NimbleWorkflow.repository.getSleep(workflowId, identifier);
        if (sleep == null) {
            NimbleWorkflow.repository.newSleepStarted(workflowId, identifier, duration);

            Workflow workflow = NimbleWorkflow.repository.getWorkflow(workflowId);
            CompleteSleepTaskInput input = new CompleteSleepTaskInput(workflow.className(), workflowId, identifier);
            scheduler.schedule(SchedulerConfig.COMPLETE_SLEEP_TASK.instance(
                    "sleep::%s::".formatted(workflowId, identifier),
                    input
            ), Instant.now().plus(duration));
            throw new WorkflowSleepingException("workflow [%s] is sleeping [%s] for [%s]".formatted(workflowId, identifier, duration));
        }
        if (sleep.isCompleted()) {
            return;
        }
        Duration elapsedSleepTime = Duration.ofMillis(Instant.now().toEpochMilli() - sleep.started().timestamp().toEpochMilli());
        throw new WorkflowSleepingException("workflow [%s] has been sleeping [%s] for [%s] out of [%s]".formatted(
                workflowId,
                identifier,
                elapsedSleepTime,
                duration
        ));
    }

    private <R extends Serializable> CompletableFuture<R> _async(Supplier<R> supplier) {
        String workflowId = WorkflowExecutor.workflowId.get();
        return CompletableFuture.supplyAsync(() -> {
            WorkflowExecutor.workflowId.set(workflowId);
            R r = supplier.get();
            WorkflowExecutor.workflowId.remove();
            return r;
        });
    }

    private CompletableFuture<Void> _async(Runnable runnable) {
        String workflowId = WorkflowExecutor.workflowId.get();
        return CompletableFuture.runAsync(() -> {
            WorkflowExecutor.workflowId.set(workflowId);
            runnable.run();
            WorkflowExecutor.workflowId.remove();
        });
    }

    private <T> T _awaitSignal(String signalName, Class<T> returnType) {
        String workflowId = WorkflowExecutor.workflowId.get();

        Signal signal = NimbleWorkflow.repository.getSignal(workflowId, signalName);
        if (signal == null) {
            NimbleWorkflow.repository.newSignalWaiting(workflowId, signalName);
            throw new AwaitingSignalException(signalName);
        }
        if (signal.isReceived()) {
            return (T) signal.value();
        }

        throw new AwaitingSignalException(signalName);
    }

    <R extends Serializable> R _activity(String activityName, Supplier<R> supplier) {
        //TODO -- encapsulate this call everywhere in the app and throw an absolute fit
        // if we are running in a thread context that doesn't have this. It would be like
        // crossing the streams bad if we don't have this.  Should also guard against it with
        // a not null constraint in the db, but it would be good to have a graceful/informative failure
        String workflowId = WorkflowExecutor.workflowId.get();

        if (NimbleWorkflow.repository.isActivityComplete(workflowId, activityName)) {
            System.out.println("this already ran, so skipping execution");
            return (R) NimbleWorkflow.repository.getActivity(workflowId, activityName).output();
        }
        NimbleWorkflow.repository.newActivityStarted(workflowId, activityName);
        System.out.println("before");
        R output = supplier.get();
        System.out.println("after");
        NimbleWorkflow.repository.completeActivity(workflowId, activityName, output);
        return output;
    }

    void _activity(String activityName, Runnable runnable) {
        String workflowId = WorkflowExecutor.workflowId.get();
        if (NimbleWorkflow.repository.isActivityComplete(workflowId, activityName)) {
            System.out.println("this already ran, so skipping execution");
            return;
        }
        NimbleWorkflow.repository.newActivityStarted(workflowId, activityName);
        System.out.println("before");
        runnable.run();
        System.out.println("after");
        NimbleWorkflow.repository.completeActivity(workflowId, activityName, null);
    }

}
