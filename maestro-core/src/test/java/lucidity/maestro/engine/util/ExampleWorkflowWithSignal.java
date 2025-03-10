package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.signal.SignalFunction;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;

@WorkflowInterface
public interface ExampleWorkflowWithSignal {

    @WorkflowFunction
    public String execute(Integer input);

    @SignalFunction
    void doContinue(boolean doContinue);

}
