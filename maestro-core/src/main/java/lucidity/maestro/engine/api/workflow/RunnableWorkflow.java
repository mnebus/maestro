package lucidity.maestro.engine.api.workflow;

public interface RunnableWorkflow<R,P> {

    R execute(P param);
}
