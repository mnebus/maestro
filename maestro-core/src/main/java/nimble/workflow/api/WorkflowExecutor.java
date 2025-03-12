package nimble.workflow.api;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
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

    public static final ThreadLocal<String> workflowId = new ThreadLocal<>();

    private final Scheduler scheduler;

    public WorkflowExecutor(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public <R> R getWorkflowOutput(String workflowId, Class<R> outputClass) {
        return (R) NimbleWorkflow.repository.getWorkflow(workflowId).output();
    }

    public <P, T extends Serializable> void signalWorkflow(Class<? extends RunnableWorkflow<?, P>> workflowClass, String workflowId, String signalName, T signalValue) {
        TaskInstance<SignalWorkflowTaskInput> instance = SchedulerConfig.SIGNAL_WORKFLOW_TASK.instance(
                "signal::%s::%s".formatted(workflowId,signalName),
                new SignalWorkflowTaskInput(workflowClass, workflowId, signalName, signalValue));
        scheduler.schedule(instance, Instant.now());
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> NimbleWorkflow.repository.isSignalReceived(workflowId,signalName));

    }

    public <P> void runWorkflow(Class<? extends RunnableWorkflow<?, P>> workflowClass, P workflowParam, String workflowId) {
        TaskInstance<StartWorkflowTaskInput> instance = SchedulerConfig.START_WORKFLOW_TASK.instance(
                "workflow::%s".formatted(workflowId),
                new StartWorkflowTaskInput(workflowClass, workflowParam, workflowId));
        NimbleWorkflow.repository.newWorkflowScheduled(workflowId, workflowClass, workflowParam);
        scheduler.schedule(instance,
                Instant.now());
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> NimbleWorkflow.repository.hasWorkflowStarted(workflowId));

    }

}
