package lucidity.maestro.engine.util;

public class ExampleSimpleWorkflowImpl implements ExampleSimpleWorkflow {
     public String execute(Integer param) {
        System.out.println("executing with param [%s]".formatted(param));
        return param.toString();
    }
}
