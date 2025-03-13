package nimble.workflow.internal;

import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import nimble.workflow.NimbleWorkflow;
import nimble.workflow.api.WorkflowExecutor;
import nimble.workflow.model.Workflow;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

public class SchedulerConfig {

    //TODO -- move all of scheduling activities into this class

    private static Set<Object> workflowDependencies;
    public static final OneTimeTask<CompleteSleepTaskInput> COMPLETE_SLEEP_TASK = Tasks.oneTime("CompleteSleepTask", CompleteSleepTaskInput.class)
            .execute((inst, ctx) -> {
                CompleteSleepTaskInput data = inst.getData();
                NimbleWorkflow.repository.sleepCompleted(data.workflowId(), data.sleepIdentifier());
                try {
                    resumeExecution(data.workflowId(), Class.forName(data.workflowClassName()));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
    public static final OneTimeTask<SignalWorkflowTaskInput> SIGNAL_WORKFLOW_TASK = Tasks.oneTime("SignalWorkflowTask", SignalWorkflowTaskInput.class)
            .execute((inst, ctx) -> {
                SignalWorkflowTaskInput data = inst.getData();
                NimbleWorkflow.repository.signalReceived(data.workflowId(), data.signalName(), data.signalValue());
                resumeExecution(data.workflowId(), data.workflowClass());

            });
    public static final OneTimeTask<StartWorkflowTaskInput> START_WORKFLOW_TASK = Tasks.oneTime("StartWorkflowTask", StartWorkflowTaskInput.class)
            .execute((inst, ctx) -> {
                StartWorkflowTaskInput data = inst.getData();
                NimbleWorkflow.repository.workflowStarted(data.workflowId());
                resumeExecution(data.workflowId(), data.workflowClass());
            });

    public static final OneTimeTask<WaitForConditionTaskInput> WAIT_FOR_CONDITION_TASK = Tasks.oneTime("WaitForConditionTask", WaitForConditionTaskInput.class)
            .onFailure((executionComplete, executionOperations) -> {
                WaitForConditionTaskInput data = (WaitForConditionTaskInput) executionComplete.getExecution().taskInstance.getData();
                Throwable cause = executionComplete.getCause().orElse(null);
                System.out.println("Failed to satisfy condition: " + cause);
                if (cause instanceof ConditionNotSatisfiedException) {
                    Instant rescheduleTime = Instant.now().plus(data.pollRate());
                    System.out.printf("rescheduling condition reevaluation at [%s]%n", rescheduleTime);
                    executionOperations.reschedule(executionComplete, rescheduleTime);
                } else {
                    //TODO -- mark workflow and condition failed
                    executionOperations.remove();
                }

            })
            .execute((inst, ctx) -> {
                WaitForConditionTaskInput data = inst.getData();
                try {
                    resumeExecution(data.workflowId(), Class.forName(data.workflowClassName()));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });

    public static void initialize(Set<Object> workflowDependencies) {
        SchedulerConfig.workflowDependencies = workflowDependencies;
    }

    private static void resumeExecution(String workflowId, Class workflowClass) {
        Workflow workflow = NimbleWorkflow.repository.getWorkflow(workflowId);
        try {
            RunnableWorkflow instance = instantiate(workflowClass);
            WorkflowExecutor.workflowId.set(workflowId);
            Object output = instance.execute(workflow.input());
            WorkflowExecutor.workflowId.remove();
            NimbleWorkflow.repository.workflowCompleted(workflowId, output);
        } catch (AwaitingSignalException awaitingSignalException) {
            System.out.printf("Pausing execution of workflow [%s] to wait for signal [%s]%n", workflowId, awaitingSignalException.getSignal());
        } catch (WorkflowSleepingException e) {
            System.out.println(e.getMessage());
        } catch (ConditionNotSatisfiedException e) {
            System.out.println(e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RunnableWorkflow instantiate(Class workflowClass) {
        try {
            Constructor constructor = findInjectableConstructor(workflowClass);

            Object[] args = Arrays.stream(constructor.getParameterTypes())
                    .map(type -> workflowDependencies.stream()
                            .filter(o -> type.isAssignableFrom(o.getClass()))
                            .findFirst()
                            .orElseThrow(() -> new NoSuchElementException("Could not find a constructor arg match for type [%s] on class [%s]".formatted(type, workflowClass))))
                    .toArray();
            return (RunnableWorkflow) constructor.newInstance(args);

        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Class must have only one public constructor or have a single public constructor annotated with @Inject
     *
     * @param clazz
     * @return
     */
    private static Constructor findInjectableConstructor(Class clazz) {
        List<Constructor> list = Arrays.stream(clazz.getConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toList();
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new RuntimeException("still need to handle multiple constructors with @Inject");
    }
}
