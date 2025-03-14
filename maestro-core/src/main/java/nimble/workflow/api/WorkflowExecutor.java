package nimble.workflow.api;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.google.common.flogger.FluentLogger;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import nimble.workflow.NimbleWorkflow;
import nimble.workflow.internal.SchedulerConfig;
import nimble.workflow.internal.SignalWorkflowTaskInput;
import nimble.workflow.internal.StartWorkflowTaskInput;
import org.awaitility.Awaitility;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class WorkflowExecutor {

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    public static final ThreadLocal<String> workflowId = new ThreadLocal<>();

    private final Scheduler scheduler;

    public WorkflowExecutor(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public <R> R getWorkflowOutput(String workflowId, Class<R> outputClass) {
        return (R) NimbleWorkflow.repository.getWorkflow(workflowId).output();
    }

    public <T extends Serializable> void signalWorkflow(String workflowId, String signalName, T signalValue) {
        logger.atInfo().log("received signal [%s::%s]", workflowId, signalName);
        TaskInstance<SignalWorkflowTaskInput> instance = SchedulerConfig.SIGNAL_WORKFLOW_TASK.instance(
                "signal::%s::%s".formatted(workflowId, signalName),
                new SignalWorkflowTaskInput(workflowId, signalName, signalValue));
        scheduler.schedule(instance, Instant.now());
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> NimbleWorkflow.repository.isSignalReceived(workflowId, signalName));

    }

    public <P> void runWorkflow(Class<? extends RunnableWorkflow<?, P>> workflowClass, P workflowParam, String workflowId) {
        logger.atInfo().log("scheduling new workflow [%s] of type [%s]", workflowId, workflowClass.getName());
        NimbleWorkflow.repository.newWorkflowScheduled(workflowId, workflowClass, workflowParam);
        TaskInstance<StartWorkflowTaskInput> instance = SchedulerConfig.RUN_WORKFLOW_TASK.instance(
                "workflow::%s".formatted(workflowId),
                new StartWorkflowTaskInput(workflowId));
        scheduler.schedule(instance,
                Instant.now());
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> NimbleWorkflow.repository.hasWorkflowStarted(workflowId));

    }

}
