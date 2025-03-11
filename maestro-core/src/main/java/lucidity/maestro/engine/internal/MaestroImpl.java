package lucidity.maestro.engine.internal;

import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.activity.Activity;
import lucidity.maestro.engine.api.activity.ActivityOptions;
import lucidity.maestro.engine.api.signal.SignalFunction;
import lucidity.maestro.engine.api.throwable.UnregisteredWorkflowException;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowActions;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.entity.EventModel;
import lucidity.maestro.engine.internal.handler.ActivityInvocationHandler;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Json;
import lucidity.maestro.engine.internal.util.Util;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;

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

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class MaestroImpl implements Maestro {
    private final Map<Class<?>, Object> typeToActivity = new HashMap<>();
    private final Map<String, Class<? extends RunnableWorkflow>> simpleNameToWorkflowImplType = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final EventRepo eventRepo;

    private final WorkflowActions workflowActions;

    public MaestroImpl(EventRepo eventRepo, DataSource dataSource) {
        this.workflowActions = new WorkflowActions.WorkflowActionsImpl(this, eventRepo, dataSource);
        this.eventRepo = eventRepo;
    }

    public List<EventModel> getWorkflowEvents(String workflowId) {
        return eventRepo.get(workflowId);
    }

    public void registerWorkflowImplementationTypes(Class<? extends RunnableWorkflow>... workflowImplementationClasses) {

        Arrays.stream(workflowImplementationClasses)
                .forEach(workflowImplementationClass -> simpleNameToWorkflowImplType.put(workflowImplementationClass.getSimpleName(), workflowImplementationClass));
    }

//    private <T extends Class<RunnableWorkflow>> T instrumentClass(T clazz) {
//        try {
//            new ByteBuddy()
//                    .subclass(clazz)
//                    .defineConstructor(Opcodes.ACC_PUBLIC)
//                    .withParameters(WorkflowOptions.class)
//                    .intercept(MethodCall.invoke(Object.class.getConstructor()).andThen(FieldAccessor.ofField("_workflowOptions").setsArgumentAt(0)))
//                    .defineField("_workflowOptions", WorkflowOptions.class, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
//                    .method(named("execute"))
//                    .intercept(
//                            MethodDelegation.to(
//                                    new WorkflowExecutionInterceptor(workflowOptions, eventRepo, executor)
//                            )
//                    )
//    //                .method(isAnnotatedWith(SignalFunction.class))
//    //                .intercept(MethodDelegation.to(new WorkflowSignalInterceptor()))
//                    .make()
//                    .load(getClass().getClassLoader())
//                    .getLoaded();
//        } catch (NoSuchMethodException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    public void registerActivity(Object activity) {
        registerActivity(activity, new ActivityOptions(Duration.ofMinutes(5)));
    }

    public void registerActivity(Object activity, ActivityOptions options) {
        typeToActivity.put(Util.getActivityInterface(activity.getClass()), proxyActivity(activity, options));
    }

    @Override
    public WorkflowActions workflowActions() {
        return this.workflowActions;
    }


    public <T extends RunnableWorkflow> T newWorkflow(Class<T> clazz, WorkflowOptions workflowOptions) {
        if (simpleNameToWorkflowImplType.get(clazz.getSimpleName()) == null) {
            throw new UnregisteredWorkflowException(clazz);
        }

        T instance = Util.createInstance(new ByteBuddy()
                .subclass(clazz)
                .method(named("execute"))
                .intercept(
                        MethodDelegation.to(
                                new WorkflowExecutionInterceptor(workflowOptions, eventRepo)
                        )
                )
                .method(isAnnotatedWith(SignalFunction.class))
                .intercept(MethodDelegation.to(new WorkflowSignalMethodInterceptor(eventRepo, workflowOptions, executor)))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded());

        populateSuperclassAnnotatedFields(instance);

        return instance;


    }

    public Class<? extends RunnableWorkflow> getWorkflowImplType(String simpleName) {
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

    private void populateSuperclassAnnotatedFields(Object instance) {
        Field[] fields = instance.getClass().getSuperclass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Activity.class)) {
                Object activity = typeToActivity.get(field.getType());
                Util.setField(field, instance, activity);
            }
        }
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
        Class<? extends RunnableWorkflow> workflowClass = getWorkflowImplType(workflowStartedEvent.className());
        Method workflowMethod = Util.findWorkflowMethod(workflowClass);

        Type[] paramTypes = workflowMethod.getGenericParameterTypes();
        Object[] finalArgs = Arrays.stream(paramTypes)
                .findFirst()
                .map(paramType -> Json.deserialize(workflowStartedEvent.data(), paramType))
                .map(deserialized -> new Object[]{deserialized})
                .orElse(Util.getDefaultArgs(paramTypes.length));

        WorkflowOptions workflowOptions = Json.deserialize(workflowStartedEvent.metadata(), WorkflowOptions.class);
        RunnableWorkflow proxy = newWorkflow(workflowClass, workflowOptions);

        executor.submit(() -> proxy.execute(finalArgs[0]));
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
                WorkflowSignalMethodInterceptor.callSuper.set(true);
                signalMethod.invoke(workflow, finalArgs);
                WorkflowSignalMethodInterceptor.callSuper.set(false);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
