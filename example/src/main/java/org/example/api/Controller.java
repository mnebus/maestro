package org.example.api;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import org.example.workflow.OrderWorkflow;
import org.example.workflow.OrderWorkflowImpl;
import org.example.workflow.model.Order;
import org.example.workflow.model.ShippingConfirmation;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class Controller {

    @PostMapping("/order/{orderId}")
    public void order(@PathVariable String orderId, @RequestBody Order order) throws ExecutionException, InterruptedException {
        OrderWorkflow workflow = MaestroService.newWorkflow2(OrderWorkflowImpl.class, new WorkflowOptions(orderId));
        workflow.execute(order);
    }

    @PostMapping("/confirmation/{trackingNumber}")
    public void signalWorkflow(@PathVariable String trackingNumber, @RequestBody ShippingConfirmation shippingConfirmation) {
        OrderWorkflow workflow = MaestroService.newWorkflow2(OrderWorkflowImpl.class, new WorkflowOptions(trackingNumber));
        workflow.confirmShipped(shippingConfirmation);
    }
}