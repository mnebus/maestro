package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.activity.Activity;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;

public class ExampleWorkflowWithActivity implements RunnableWorkflow<String, ExampleWorkflowWithActivity.ExampleWorkflowWithActivityParam> {

    @Activity
    private ExampleMathActivity exampleMathActivity;

    public record ExampleWorkflowWithActivityParam(Integer startWith, Long multiplyBy, Long subtract) {}
    @Override
    public String execute(ExampleWorkflowWithActivityParam param) {
        System.out.println("Executing ExampleWorkflowWithActivityImpl with param [%s]".formatted(param));

        Long multiplyResult = exampleMathActivity.multiply(
                new ExampleMathActivity.MathOperationInput(param.startWith().longValue(), param.multiplyBy())
        );

        Long subtractResult = exampleMathActivity.subtract(
                new ExampleMathActivity.MathOperationInput(multiplyResult, param.subtract())
        );

        return subtractResult.toString();
    }
}
