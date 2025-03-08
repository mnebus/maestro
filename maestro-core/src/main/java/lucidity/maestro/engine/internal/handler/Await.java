package lucidity.maestro.engine.internal.handler;

import lucidity.maestro.engine.api.throwable.AbortWorkflowExecutionError;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.dto.WorkflowContextManager;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.exception.WorkflowCorrelationStatusConflict;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.Status;
import lucidity.maestro.engine.internal.repo.EventRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Supplier;

public class Await {
    private static final Logger logger = LoggerFactory.getLogger(Await.class);
    private final MaestroImpl maestroImpl;
    private final EventRepo eventRepo;

    public Await(MaestroImpl maestroImpl, EventRepo eventRepo) {

        this.maestroImpl = maestroImpl;
        this.eventRepo = eventRepo;
    }

    public void await(Supplier<Boolean> condition) {
        WorkflowContext workflowContext = WorkflowContextManager.get();
        Long correlationNumber = WorkflowContextManager.getCorrelationNumber();

        EventEntity existingCompletedAwait = eventRepo.get(workflowContext.workflowId(), correlationNumber, Status.COMPLETED);
        if (existingCompletedAwait != null) {
            maestroImpl.applySignals(workflowContext, existingCompletedAwait.sequenceNumber());
            return;
        }

        try {
            eventRepo.saveWithRetry(() -> new EventEntity(
                    UUID.randomUUID().toString(), workflowContext.workflowId(),
                    correlationNumber, eventRepo.getNextSequenceNumber(workflowContext.workflowId()),
                    Category.AWAIT, null, null,
                    null, Status.STARTED, null, null
            ));
        } catch (WorkflowCorrelationStatusConflict e) {
            logger.debug(e.getMessage());
        }

        Long nextSequenceNumber = eventRepo.getNextSequenceNumber(workflowContext.workflowId());
        maestroImpl.applySignals(workflowContext, nextSequenceNumber);

        if (!condition.get()) {
            eventRepo.saveWithRetry(() -> new EventEntity(
                    UUID.randomUUID().toString(), workflowContext.workflowId(),
                    correlationNumber, eventRepo.getNextSequenceNumber(workflowContext.workflowId()),
                    Category.AWAIT, null, null,
                    null, Status.UNSATISFIED, null, null
            ));

            throw new AbortWorkflowExecutionError("Abandoning workflow execution because of await condition wasn't satisfied " +
                    "with workflowId: " + workflowContext.workflowId() + ", correlationNumber " + correlationNumber);
        }

        try {
            eventRepo.saveWithRetry(() -> new EventEntity(
                    UUID.randomUUID().toString(), workflowContext.workflowId(),
                    correlationNumber, nextSequenceNumber, Category.AWAIT,
                    null, null, null,
                    Status.COMPLETED, null, null
            ));
        } catch (WorkflowCorrelationStatusConflict e) {
            throw new AbortWorkflowExecutionError("Abandoning workflow execution because of conflict with completed activity " +
                    "with workflowId: " + workflowContext.workflowId() + ", correlationNumber " + correlationNumber);
        }
    }
}
