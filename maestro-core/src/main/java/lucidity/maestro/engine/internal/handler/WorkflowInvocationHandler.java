package lucidity.maestro.engine.internal.handler;

import lucidity.maestro.engine.api.signal.SignalFunction;
import lucidity.maestro.engine.api.throwable.AbortWorkflowExecutionError;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.dto.WorkflowContextManager;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.entity.Status;
import lucidity.maestro.engine.internal.exception.WorkflowCorrelationStatusConflict;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import lucidity.maestro.engine.internal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class WorkflowInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowInvocationHandler.class);
    private final Object target;
    private final WorkflowOptions options;
    private final EventRepo eventRepo;
    private final ExecutorService executor;

    public WorkflowInvocationHandler(Object target, WorkflowOptions options, EventRepo eventRepo, ExecutorService executor) {

        this.target = target;
        this.options = options;
        this.eventRepo = eventRepo;
        this.executor = executor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if (Util.shouldSkip(method)) return method.invoke(target, args);

            if (Util.isAnnotatedWith(method, target, WorkflowFunction.class)) {
                String input = Json.serializeFirst(args);

                WorkflowContextManager.set(new WorkflowContext(options.workflowId(), 0L, null, target));
                Long correlationNumber = WorkflowContextManager.getCorrelationNumber();

                try {
                    eventRepo.saveWithRetry(() -> new EventEntity(
                            UUID.randomUUID().toString(), options.workflowId(),
                            correlationNumber, eventRepo.getNextSequenceNumber(options.workflowId()),
                            Category.WORKFLOW, target.getClass().getSimpleName(), method.getName(),
                            input, Status.STARTED, null, Json.serialize(options)
                    ));
                } catch (WorkflowCorrelationStatusConflict e) {
                    logger.debug(e.getMessage());
                }

                Object output = method.invoke(target, args);

                try {
                    eventRepo.saveWithRetry(() -> new EventEntity(
                            UUID.randomUUID().toString(), options.workflowId(),
                            correlationNumber, eventRepo.getNextSequenceNumber(options.workflowId()),
                            Category.WORKFLOW, target.getClass().getSimpleName(), method.getName(),
                            Json.serialize(output), Status.COMPLETED, null, null
                    ));
                } catch (WorkflowCorrelationStatusConflict e) {
                    logger.debug(e.getMessage());
                } finally {
                    WorkflowContextManager.clear();
                }

                return output;
            } else if (Util.isAnnotatedWith(method, target, SignalFunction.class)) {
                eventRepo.saveWithRetry(() -> new EventEntity(
                        UUID.randomUUID().toString(), options.workflowId(),
                        null, eventRepo.getNextSequenceNumber(options.workflowId()),
                        Category.SIGNAL, target.getClass().getSimpleName(), method.getName(),
                        Json.serializeFirst(args), Status.RECEIVED, null, null
                ));

                EventEntity existingStartedWorkflow = eventRepo.get(
                        options.workflowId(), Category.WORKFLOW, Status.STARTED
                );

                if (existingStartedWorkflow != null) {
                    Method workflowMethod = Util.findWorkflowMethod(proxy.getClass());

                    Type[] paramTypes = workflowMethod.getGenericParameterTypes();
                    Object[] finalArgs = Arrays.stream(paramTypes)
                            .findFirst()
                            .map(paramType -> Json.deserialize(existingStartedWorkflow.data(), paramType))
                            .map(deserialized -> new Object[]{deserialized})
                            .orElse(Util.getDefaultArgs(paramTypes.length));

                    executor.submit(() -> workflowMethod.invoke(proxy, finalArgs));
                }

                return null;
            } else {
                return method.invoke(target, args);
            }
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AbortWorkflowExecutionError) return null;
            else throw e;
        }
    }
}
