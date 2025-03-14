package lucidity.maestro.engine.internal;

import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.entity.Status;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import lucidity.maestro.engine.internal.util.Util;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class WorkflowSignalMethodInterceptor {

    public static final ThreadLocal<Boolean> callSuper = ThreadLocal.withInitial(() -> false);

    private final EventRepo eventRepo;
    private final WorkflowOptions options;
    private final ExecutorService executor;

    public WorkflowSignalMethodInterceptor(EventRepo eventRepo, WorkflowOptions options, ExecutorService executor) {

        this.eventRepo = eventRepo;
        this.options = options;
        this.executor = executor;
    }

    private Method findExecuteMethod(Class clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> "execute".equals(method.getName()))
                .filter(method -> method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
    }

    @RuntimeType
    public Object intercept(@Argument(0) @RuntimeType Object arg, @This RunnableWorkflow currentObject, @Super RunnableWorkflow zuper, @SuperCall Callable<?> zuperCall, @Origin Method method) {

        if (callSuper.get()) {
            try {
                return zuperCall.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        String className = zuper.getClass().getSimpleName();
        final String parsedClassName = className.substring(0, className.indexOf('$'));
        eventRepo.saveWithRetry(() -> new EventEntity(
                UUID.randomUUID().toString(), options.workflowId(),
                null, eventRepo.getNextSequenceNumber(options.workflowId()),
                Category.SIGNAL, parsedClassName, method.getName(),
                Json.serialize(arg), Status.RECEIVED, null, null
        ));

        EventEntity existingStartedWorkflow = eventRepo.get(
                options.workflowId(), Category.WORKFLOW, Status.STARTED
        );

        if (existingStartedWorkflow != null) {
            Method workflowMethod = findExecuteMethod(currentObject.getClass());

            Type[] paramTypes = workflowMethod.getGenericParameterTypes();
            Object[] finalArgs = Arrays.stream(paramTypes)
                    .findFirst()
                    .map(paramType -> Json.deserialize(existingStartedWorkflow.data(), paramType))
                    .map(deserialized -> new Object[]{deserialized})
                    .orElse(Util.getDefaultArgs(paramTypes.length));
            workflowMethod.setAccessible(true);

            executor.submit( () -> currentObject.execute(finalArgs[0]));
        }
        return null;


    }
}
