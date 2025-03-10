package lucidity.maestro.engine.util;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.workflow.WorkflowActions;

import java.time.Duration;

public class ExampleWorkflowWithSleepImpl implements ExampleWorkflowWithSleep {
    @Override
    public String execute(Integer param) {
        System.out.println("Executing ExampleWorkflowWithSleepImpl with param [%s]".formatted(param));
        MaestroService.sleep(Duration.ofSeconds(2));
        return param.toString();
    }
}
