package lucidity.maestro.engine.util;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.signal.SignalFunction;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;

public class ExampleWorkflowWithSignal implements RunnableWorkflow<String, Integer> {

    private boolean doContinue = false;

    @Override
    public String execute(Integer input) {
        MaestroService.await(() -> this.doContinue);
        return input.toString();
    }

    @SignalFunction
    public void doContinue(boolean doContinue) {
        this.doContinue = doContinue;
    }
}
