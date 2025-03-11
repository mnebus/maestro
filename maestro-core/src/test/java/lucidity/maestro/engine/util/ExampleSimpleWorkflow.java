package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;

@WorkflowInterface
public interface ExampleSimpleWorkflow extends RunnableWorkflow<String,Integer> {

    @WorkflowFunction
    String execute(Integer param);
}
