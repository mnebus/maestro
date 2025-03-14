package org.example.workflow;

import lucidity.maestro.engine.api.signal.SignalFunction;
import lucidity.maestro.engine.api.workflow.RunnableWorkflow;
import lucidity.maestro.engine.api.workflow.WorkflowFunction;
import lucidity.maestro.engine.api.workflow.WorkflowInterface;
import org.example.workflow.model.Order;
import org.example.workflow.model.OrderFinalized;
import org.example.workflow.model.ShippingConfirmation;

import java.util.concurrent.ExecutionException;

@WorkflowInterface
public interface OrderWorkflow extends RunnableWorkflow<OrderFinalized, Order> {

    @WorkflowFunction
    OrderFinalized execute(Order order);

    @SignalFunction
    void confirmShipped(ShippingConfirmation confirmation);
}
