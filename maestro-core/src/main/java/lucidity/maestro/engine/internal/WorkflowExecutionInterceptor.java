package lucidity.maestro.engine.internal;

import lucidity.maestro.engine.api.throwable.AbortWorkflowExecutionError;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.dto.WorkflowContextManager;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.entity.Status;
import lucidity.maestro.engine.internal.exception.WorkflowCorrelationStatusConflict;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Super;
import net.bytebuddy.implementation.bind.annotation.This;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class WorkflowExecutionInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionInterceptor.class);

    private final WorkflowOptions options;
    private final EventRepo eventRepo;

    public WorkflowExecutionInterceptor(WorkflowOptions options, EventRepo eventRepo) {


        this.options = options;
        this.eventRepo = eventRepo;
    }

//    @RuntimeType
//    public Object intercept(@Argument(0) @RuntimeType Object arg, @Super RunnableWorkflow zuper) {
//        System.out.println("before workflow execute with options " + this.options.workflowId());
//        try {
//            return zuper.execute(arg);
//        } finally {
//            System.out.println("after workflow execution");
//        }
//    }

    @RuntimeType
    public Object intercept(@Argument(0) @RuntimeType Object arg, @Super RunnableWorkflow zuper, @This RunnableWorkflow currentObject) {
        try {
            String input = Json.serializeFirst(new Object[]{arg});

            WorkflowContextManager.set(new WorkflowContext(options.workflowId(), 0L, null, currentObject));
            Long correlationNumber = WorkflowContextManager.getCorrelationNumber();

            String className = zuper.getClass().getSimpleName();
            final String parsedClassName = className.substring(0, className.indexOf('$'));

            try {
                eventRepo.saveWithRetry(() -> new EventEntity(
                        UUID.randomUUID().toString(), options.workflowId(),
                        correlationNumber, eventRepo.getNextSequenceNumber(options.workflowId()),
                        Category.WORKFLOW, parsedClassName, "execute",
                        input, Status.STARTED, null, Json.serialize(options)
                ));
            } catch (WorkflowCorrelationStatusConflict e) {
                logger.debug(e.getMessage());
            }

            Object output = zuper.execute(arg);

            try {
                eventRepo.saveWithRetry(() -> new EventEntity(
                        UUID.randomUUID().toString(), options.workflowId(),
                        correlationNumber, eventRepo.getNextSequenceNumber(options.workflowId()),
                        Category.WORKFLOW, parsedClassName, "execute",
                        Json.serialize(output), Status.COMPLETED, null, null
                ));
            } catch (WorkflowCorrelationStatusConflict e) {
                logger.debug(e.getMessage());
            } finally {
                WorkflowContextManager.clear();
            }

            return output;

        } catch (Exception e) {
            if (e.getCause() instanceof AbortWorkflowExecutionError) {
                logger.info("execution stopped because cause was AbortWorkflowExecutionError");
                return null;
            } else throw e;
        } catch (AbortWorkflowExecutionError error) {
            logger.info("execution stopped by catching abort error", error);
            return null;
        }
    }
}
