package nimble.workflow.api;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.google.common.flogger.FluentLogger;
import nimble.workflow.NimbleWorkflow;
import nimble.workflow.internal.*;
import nimble.workflow.model.*;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class WorkflowFunctions {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

    // TODO -- add an optional polling interval for condition reevaluation
    public static void awaitCondition(String conditionIdentifier, Supplier<Boolean> condition) {
        SINGLETON._awaitCondition(conditionIdentifier, condition);
    }

    public static <T> T awaitSignal(String signalName, Class<T> returnType) {
        return SINGLETON._awaitSignal(signalName, returnType);
    }

    private void _awaitCondition(String conditionIdentifier, Supplier<Boolean> conditionSupplier) {
        String workflowId = WorkflowExecutor.workflowId.get();
        Condition condition = NimbleWorkflow.repository.getCondition(workflowId, conditionIdentifier);
        String conditionKey = "condition::%s::%s::%s".formatted(workflowId, conditionIdentifier, UUID.randomUUID().toString());
        if (condition == null) {
            NimbleWorkflow.repository.newConditionWaiting(workflowId, conditionIdentifier);
            condition = NimbleWorkflow.repository.getCondition(workflowId, conditionIdentifier);
        }
        if (condition.isSatisfied()) {
            System.out.println("condition has been previously satisfied");
            return;
        }

        if (conditionSupplier.get()) {
            NimbleWorkflow.repository.conditionSatisfied(workflowId, conditionIdentifier);
            return;
        }
        Workflow workflow = NimbleWorkflow.repository.getWorkflow(workflowId);
        WaitForConditionTaskInput input = new WaitForConditionTaskInput(
                workflow.className(), workflowId, Duration.ofSeconds(3));
        scheduler.schedule(SchedulerConfig.WAIT_FOR_CONDITION_TASK.instance(
                conditionKey, input), Instant.now().plus(Duration.ofSeconds(3)));
        throw new ConditionNotSatisfiedException("workflow condition [%s] was not satisfied".formatted(conditionKey));

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
        Activity activity = initActivity(workflowId, activityName);

        if (activityHasAlreadyExecuted(activity)) {
            return activityOutput(activity);
        }

        R output = supplier.get();
        completeActivity(activity, output);
        return output;
    }

    void _activity(String activityName, Runnable runnable) {
        String workflowId = WorkflowExecutor.workflowId.get();
        Activity activity = initActivity(workflowId, activityName);
        if (activityHasAlreadyExecuted(activity)) {
            return;
        }
        runnable.run();
        completeActivity(activity, null);
    }

    private <R extends Serializable> R activityOutput(Activity activity) {
        return (R) NimbleWorkflow.repository.getActivity(activity.workflowId(), activity.name()).output();
    }

    private Activity initActivity(String workflowId, String activityName) {
        Activity activity = NimbleWorkflow.repository.getActivity(workflowId, activityName);
        logger.atInfo().log("starting workflow activity [%s::%s]", workflowId, activityName);
        if (activity == null) {
            logger.atInfo().log("registering new activity [%s::%s] for initial execution", workflowId, activityName);
            NimbleWorkflow.repository.newActivityStarted(workflowId, activityName);
            activity = NimbleWorkflow.repository.getActivity(workflowId, activityName);
        }
        return activity;
    }

    private boolean activityHasAlreadyExecuted(Activity activity) {
        if (activity.isCompleted()) {
            logger.atInfo().log("activity [%s] completed with result from prior execution", activity.key());
            return true;
        }
        return false;
    }

    private <R extends Serializable> void completeActivity(Activity activity, R output) {
        NimbleWorkflow.repository.completeActivity(activity.workflowId(), activity.name(), output);
        logger.atInfo().log("activity [%s] completed for the first time", activity.key());
    }

}
