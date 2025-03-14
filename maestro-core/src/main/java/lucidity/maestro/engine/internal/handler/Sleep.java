package lucidity.maestro.engine.internal.handler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import lucidity.maestro.engine.api.throwable.AbortWorkflowExecutionError;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.dto.WorkflowContextManager;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.entity.Status;
import lucidity.maestro.engine.internal.exception.WorkflowCorrelationStatusConflict;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class Sleep {
    private static final Logger logger = LoggerFactory.getLogger(Sleep.class);
    private final OneTimeTask<SleepData> task = initializeTask();
    private final Scheduler scheduler;
    private final MaestroImpl maestroImpl;
    private final EventRepo eventRepo;

    public Sleep(MaestroImpl maestroImpl, EventRepo eventRepo, DataSource dataSource) {
        this.scheduler = this.initializeScheduler(dataSource);
        this.maestroImpl = maestroImpl;
        this.eventRepo = eventRepo;
    }

    public void sleep(Duration duration) {
        WorkflowContext workflowContext = WorkflowContextManager.get();
        Long correlationNumber = WorkflowContextManager.getCorrelationNumber();

        EventEntity existingCompletedSleep = eventRepo.get(workflowContext.workflowId(), correlationNumber, Status.COMPLETED);
        if (existingCompletedSleep != null) {
            maestroImpl.applySignals(workflowContext, existingCompletedSleep.sequenceNumber());
            return;
        }

        try {
            eventRepo.saveWithRetry(() -> new EventEntity(
                    UUID.randomUUID().toString(), workflowContext.workflowId(),
                    correlationNumber, eventRepo.getNextSequenceNumber(workflowContext.workflowId()),
                    Category.SLEEP, null, null,
                    Json.serialize(duration), Status.STARTED, null, null
            ));
        } catch (WorkflowCorrelationStatusConflict e) {
            logger.debug(e.getMessage());
        }

        String id = workflowContext.workflowId() + "-" + correlationNumber;
        scheduler.schedule(task.instance(id, new SleepData(workflowContext.workflowId(), correlationNumber)), Instant.now().plus(duration));

        WorkflowContextManager.clear();
        throw new AbortWorkflowExecutionError("Scheduled Sleep");
    }

    private void completeSleep(String workflowId, Long correlationNumber) {
        Long nextSequenceNumber = eventRepo.getNextSequenceNumber(workflowId);

        try {
            eventRepo.saveWithRetry(() -> new EventEntity(
                    UUID.randomUUID().toString(), workflowId,
                    correlationNumber, nextSequenceNumber, Category.SLEEP,
                    null, null, null,
                    Status.COMPLETED, null, null
            ));
        } catch (WorkflowCorrelationStatusConflict e) {
            logger.debug(e.getMessage());
        }

        EventEntity existingStartedWorkflow = eventRepo.get(
                workflowId, Category.WORKFLOW, Status.STARTED
        );

        maestroImpl.replayWorkflow(existingStartedWorkflow);
    }

    private OneTimeTask<SleepData> initializeTask() {
        return Tasks.oneTime("generic-task", SleepData.class)
                .execute((inst, ctx) -> {
                    logger.info("completing sleep");
                    SleepData sleepData = inst.getData();
                    completeSleep(sleepData.workflowId(), sleepData.correlationNumber());
                });
    }

    private Scheduler initializeScheduler(DataSource dataSource) {
        Scheduler scheduler = Scheduler
                .create(dataSource, task)
                .pollingInterval(Duration.ofSeconds(1))
                .registerShutdownHook()
                .build();

        scheduler.start();

        return scheduler;
    }

    private record SleepData(String workflowId, Long correlationNumber) implements Serializable {
    }
}
