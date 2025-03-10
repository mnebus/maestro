package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;

@WorkflowInterface
public interface ExampleSimpleWorkflow {

    @WorkflowFunction
    String execute(Integer param);
}
