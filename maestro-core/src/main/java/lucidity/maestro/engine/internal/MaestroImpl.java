package lucidity.maestro.engine.internal;

import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.activity.Activity;
import lucidity.maestro.engine.api.activity.ActivityOptions;
import lucidity.maestro.engine.api.throwable.UnregisteredWorkflowException;
import lucidity.maestro.engine.api.workflow.Workflow;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.handler.ActivityInvocationHandler;
import lucidity.maestro.engine.internal.handler.WorkflowInvocationHandler;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import lucidity.maestro.engine.internal.util.Util;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaestroImpl implements Maestro {
    private final Map<Class<?>, Object> typeToActivity = new HashMap<>();
    private final Map<String, Class<?>> simpleNameToWorkflowImplType = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final EventRepo eventRepo;

    private final Workflow workflowActions;

    public MaestroImpl(EventRepo eventRepo, DataSource dataSource) {
        this.workflowActions = new Workflow.WorkflowImpl(this, eventRepo, dataSource);
        this.eventRepo = eventRepo;
    }

    public void registerWorkflowImplementationTypes(Class<?>... workflows) {

        Arrays.stream(workflows)
                .forEach(workflow -> simpleNameToWorkflowImplType.put(workflow.getSimpleName(), workflow));
    }

    @Override
    public void registerActivity(Object activity) {
        registerActivity(activity, new ActivityOptions(Duration.ofMinutes(5)));
    }

    public void registerActivity(Object activity, ActivityOptions options) {
        typeToActivity.put(Util.getActivityInterface(activity.getClass()), proxyActivity(activity, options));
    }

    @Override
    public Workflow workflowActions() {
        return this.workflowActions;
    }

    @SuppressWarnings("unchecked")
    public <T> T newWorkflow(Class<T> clazz, WorkflowOptions options) {
        if (simpleNameToWorkflowImplType.get(clazz.getSimpleName()) == null) {
            throw new UnregisteredWorkflowException(clazz);
        }

        T instance = Util.createInstance(clazz);
        populateAnnotatedFields(instance);
        Class<?> interfaceClass = Util.getWorkflowInterface(clazz);

        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new WorkflowInvocationHandler(instance, options, eventRepo, executor)
        );
    }

    public Class<?> getWorkflowImplType(String simpleName) {
        return simpleNameToWorkflowImplType.get(simpleName);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxyActivity(T instance, ActivityOptions options) {
        return (T) Proxy.newProxyInstance(
                instance.getClass().getClassLoader(),
                new Class<?>[]{Util.getActivityInterface(instance.getClass())},
                new ActivityInvocationHandler(instance, options, this, eventRepo)
        );
    }

    private void populateAnnotatedFields(Object instance) {
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Activity.class)) {
                Object activity = typeToActivity.get(field.getType());
                Util.setField(field, instance, activity);
            }
        }
    }

    public void replayWorkflow(EventEntity workflowStartedEvent) {
        Class<?> workflowClass = getWorkflowImplType(workflowStartedEvent.className());
        Method workflowMethod = Util.findWorkflowMethod(workflowClass);

        Type[] paramTypes = workflowMethod.getGenericParameterTypes();
        Object[] finalArgs = Arrays.stream(paramTypes)
                .findFirst()
                .map(paramType -> Json.deserialize(workflowStartedEvent.data(), paramType))
                .map(deserialized -> new Object[]{deserialized})
                .orElse(Util.getDefaultArgs(paramTypes.length));

        WorkflowOptions workflowOptions = Json.deserialize(workflowStartedEvent.metadata(), WorkflowOptions.class);
        Object proxy = newWorkflow(workflowClass, workflowOptions);

        executor.submit(() -> workflowMethod.invoke(proxy, finalArgs));
    }

    public void applySignals(WorkflowContext workflowContext, Long nextSequenceNumber) {
        Object workflow = workflowContext.workflow();
        List<EventEntity> signals = eventRepo.getSignals(workflowContext.workflowId(), nextSequenceNumber);
        for (EventEntity signal : signals) {
            Method signalMethod = Arrays.stream(workflow.getClass().getMethods())
                    .filter(m -> m.getName().equals(signal.functionName()))
                    .findFirst().get();

            Type[] paramTypes = signalMethod.getGenericParameterTypes();
            Object[] finalArgs = Arrays.stream(paramTypes)
                    .findFirst()
                    .map(paramType -> Json.deserialize(signal.data(), paramType))
                    .map(deserialized -> new Object[]{deserialized})
                    .orElse(Util.getDefaultArgs(paramTypes.length));

            try {
                signalMethod.invoke(workflow, finalArgs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
