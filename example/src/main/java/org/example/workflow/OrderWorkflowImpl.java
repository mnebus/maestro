package org.example.workflow;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.activity.Activity;
import lucidity.maestro.engine.api.async.Async;
import org.example.activity.interfaces.InventoryActivity;
import org.example.activity.interfaces.NotificationActivity;
import org.example.activity.interfaces.PaymentActivity;
import org.example.activity.model.ProductInventory;
import org.example.workflow.model.Order;
import org.example.workflow.model.OrderFinalized;
import org.example.workflow.model.OrderedProduct;
import org.example.workflow.model.ShippingConfirmation;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class OrderWorkflowImpl implements OrderWorkflow {

    @Activity
    private InventoryActivity inventoryActivity;

    @Activity
    private PaymentActivity paymentActivity;

    @Activity
    private NotificationActivity notificationActivity;

    private String trackingNumber;

    @Override
    public OrderFinalized submitOrder(Order order) throws ExecutionException, InterruptedException {

        List<OrderedProduct> reservedProducts = inventoryActivity.reserveInventory(order.orderedProducts());

        paymentActivity.processPayment(order.total());

        notificationActivity.sendOrderConfirmedEmail();

        MaestroService.await(() -> trackingNumber != null);

        // executing the following two activities in parallel
        CompletableFuture<String> emailResultFuture = Async.function(() -> notificationActivity.sendOrderShippedEmail(trackingNumber));
        CompletableFuture<List<ProductInventory>> newInventoryFuture = Async.function(() -> inventoryActivity.decreaseInventory(reservedProducts));

        // waiting for both to complete
        emailResultFuture.get();
        List<ProductInventory> newInventoryLevel = newInventoryFuture.get();

        MaestroService.sleep(Duration.ofSeconds(10)); // this can be as long as you like

        notificationActivity.sendSpecialOfferPushNotification();

        return new OrderFinalized(trackingNumber, newInventoryLevel);
    }

    @Override
    public void confirmShipped(ShippingConfirmation confirmation) {
        trackingNumber = confirmation.trackingNumber();
    }
}
