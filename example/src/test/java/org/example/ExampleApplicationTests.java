package org.example;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventModel;
import org.awaitility.Awaitility;
import org.example.activity.impl.InventoryActivityImpl;
import org.example.activity.impl.NotificationActivityImpl;
import org.example.activity.impl.PaymentActivityImpl;
import org.example.workflow.OrderWorkflow;
import org.example.workflow.OrderWorkflowImpl;
import org.example.workflow.model.Order;
import org.example.workflow.model.OrderFinalized;
import org.example.workflow.model.OrderedProduct;
import org.example.workflow.model.ShippingConfirmation;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class ExampleApplicationTests {

    @Container
    private static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer()
            .withDatabaseName("test-database")
            .withUsername("test-user")
            .withPassword("test-password");

	@Test
    void testSimpleWorkflowWithSignal() throws Exception {

        // given a configured maestro
        Maestro maestro = MaestroService.builder()
                .configureDataSource(postgresqlContainer.getUsername(), postgresqlContainer.getPassword(), postgresqlContainer.getJdbcUrl())
                .build();
        maestro.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        maestro.registerActivity(new InventoryActivityImpl());
        maestro.registerActivity(new PaymentActivityImpl());
        maestro.registerActivity(new NotificationActivityImpl(null));

        // when we create a workflow
        String workflowId = "example-workflow-id";
        OrderWorkflow workflow = MaestroService.newWorkflow2(OrderWorkflowImpl.class, new WorkflowOptions(workflowId));
        // and execute the workflow
        OrderFinalized orderFinalized = workflow.execute(new Order(new BigDecimal(10.00), Arrays.asList(new OrderedProduct("something", 1))));

        // then the workflow returns null because it's waiting on a signal
        assertNull(orderFinalized);

        // and 2 events are created in the database
        List<EventModel> events = MaestroService.getWorkflowEvents(workflowId);
        assertEquals(5, events.size());


        // and one is an AWAIT event with no end time
        assertEquals(workflowId, events.get(4).workflowId());
        assertEquals(Category.AWAIT, events.get(4).category());
        assertNull(events.get(4).endTimestamp());

        // and when we send the signal
        workflow.confirmShipped(new ShippingConfirmation("asdf asdf asdf"));

        // then there will eventually be 3 events
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() ->  MaestroService.getWorkflowEvents(workflowId).size() == 9);
        events = MaestroService.getWorkflowEvents(workflowId);

        // and we will be waiting on SLEEP
        assertNull(events.get(8).endTimestamp());
        assertEquals(Category.SLEEP, events.get(8).category());

        // and SLEEP will eventually finish
        Awaitility.await().atMost(20,TimeUnit.SECONDS)
                .until(() -> MaestroService.getWorkflowEvents(workflowId).get(8).endTimestamp() != null);

        // and the WORKFLOW is complete
        Awaitility.await().atMost(60, TimeUnit.SECONDS)
                        .until(() -> MaestroService.getWorkflowEvents(workflowId).get(0).endTimestamp() != null);



    }

}
