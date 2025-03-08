package lucidity.maestro.engine.api;

import lucidity.maestro.engine.api.activity.ActivityOptions;
import lucidity.maestro.engine.api.workflow.Workflow;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;

import java.time.Duration;

public interface Maestro {

    void registerWorkflowImplementationTypes(Class<?>... workflows);

    void registerActivity(Object activity);

    void registerActivity(Object activity, ActivityOptions activityOptions);

    Workflow workflowActions();

    <T> T newWorkflow(Class<T> clazz, WorkflowOptions options);
}
