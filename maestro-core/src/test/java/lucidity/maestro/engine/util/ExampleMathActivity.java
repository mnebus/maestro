package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.activity.ActivityInterface;

@ActivityInterface
public interface ExampleMathActivity {

    record MathOperationInput(Long leftOperand, Long rightOperand){}

    Long multiply(MathOperationInput mathOperationInput);

    Long subtract(MathOperationInput mathOperationInput);


}
