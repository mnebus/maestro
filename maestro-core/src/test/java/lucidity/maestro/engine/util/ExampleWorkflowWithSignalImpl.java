package lucidity.maestro.engine.util;

import lucidity.maestro.engine.MaestroService;

public class ExampleWorkflowWithSignalImpl implements ExampleWorkflowWithSignal {

    private boolean doContinue = false;

    @Override
    public String execute(Integer input) {
        MaestroService.await(() -> this.doContinue);
        return input.toString();
    }

    @Override
    public void doContinue(boolean doContinue) {
        this.doContinue = doContinue;
    }
}
