package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;

@WorkflowInterface
public interface ExampleWorkflowWithActivity extends RunnableWorkflow<String, ExampleWorkflowWithActivity.ExampleWorkflowWithActivityParam> {

    record ExampleWorkflowWithActivityParam(Integer startWith, Long multiplyBy, Long subtract){};

    @WorkflowFunction
    String execute(ExampleWorkflowWithActivityParam param);

}
