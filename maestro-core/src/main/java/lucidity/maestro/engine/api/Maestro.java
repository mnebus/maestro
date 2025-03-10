package lucidity.maestro.engine.api;

import lucidity.maestro.engine.api.activity.ActivityOptions;
import lucidity.maestro.engine.api.workflow.WorkflowActions;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;

public interface Maestro {

    void registerWorkflowImplementationTypes(Class<?>... workflows);

    void registerActivity(Object activity);

    void registerActivity(Object activity, ActivityOptions activityOptions);

    WorkflowActions workflowActions();

    <T> T newWorkflow(Class<T> clazz, WorkflowOptions options);
}
