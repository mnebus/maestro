package lucidity.maestro.engine.internal.util;

import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.activity.ActivityInterface;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.dto.WorkflowContext;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.repo.EventRepo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Util {

    private static final Set<String> METHODS_TO_SKIP = Set.of(
            "toString",
            "hashCode",
            "equals"
    );

    public static boolean shouldSkip(Method method) {
        return METHODS_TO_SKIP.contains(method.getName());
    }

    public static boolean isAnnotatedWith(Method method, Object target, Class<? extends Annotation> annotationClass) {
        for (Class<?> iface : target.getClass().getInterfaces()) {
            try {
                Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                if (ifaceMethod.isAnnotationPresent(annotationClass)) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Continue checking other interfaces
            }
        }
        return false;
    }

    public static Method findWorkflowMethod(Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> "execute".equals(method.getName()))
                .findFirst()
                .orElseThrow();
    }

    public static Object[] getDefaultArgs(Integer numberOfParameters) {
        if (numberOfParameters == null || numberOfParameters < 0) {
            throw new IllegalArgumentException();
        }
        if (numberOfParameters == 0) {
            return new Object[]{};
        }
        return new Object[]{null};
    }

    public static <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setField(Field field, Object instance, Object value) {
        field.setAccessible(true);

        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> getActivityInterface(Class<?> clazz) {
        return getAnnotatedInterfaceOfClass(clazz, ActivityInterface.class);
    }

    public static Class<?> getWorkflowInterface(Class<?> clazz) {
        return getAnnotatedInterfaceOfClass(clazz, WorkflowInterface.class);
    }

    private static Class<?> getAnnotatedInterfaceOfClass(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.isAnnotationPresent(annotationClass)) return iface;
        }

        throw new IllegalArgumentException("The class must implement an interface annotated with @" + annotationClass.getSimpleName());
    }
}
