package lucidity.maestro.engine;

import lucidity.maestro.engine.util.ExampleMathActivity;

public class ExampleMathActivityImpl implements ExampleMathActivity {
    @Override
    public Long multiply(MathOperationInput mathOperationInput) {
        return  mathOperationInput.leftOperand() * mathOperationInput.rightOperand();
    }

    @Override
    public Long subtract(MathOperationInput mathOperationInput) {
        return mathOperationInput.leftOperand() - mathOperationInput.rightOperand();
    }
}
