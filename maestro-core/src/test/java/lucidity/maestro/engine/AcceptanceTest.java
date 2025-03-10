package lucidity.maestro.engine;

import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.entity.Category;
import lucidity.maestro.engine.internal.entity.EventModel;
import lucidity.maestro.engine.util.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class AcceptanceTest {

    @Container
    private static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer()
            .withDatabaseName("test-database")
            .withUsername("test-user")
            .withPassword("test-password");

    @Test
    void testSimpleWorkflowWithNoSignals() throws Exception {
        // given a running postgres container
        Assertions.assertTrue(postgresqlContainer.isRunning());

        // and a configured maestro
        Maestro maestro = MaestroService.builder()
                .configureDataSource(postgresqlContainer.getUsername(), postgresqlContainer.getPassword(), postgresqlContainer.getJdbcUrl())
                .build();
        maestro.registerWorkflowImplementationTypes(ExampleSimpleWorkflowImpl.class);

        // when we create a workflow
        ExampleSimpleWorkflow exampleSimpleWorkflow = MaestroService.newWorkflow(ExampleSimpleWorkflowImpl.class, new WorkflowOptions("example-simple-workflow-id"));
        // and execute the workflow
        String output = exampleSimpleWorkflow.execute(666);

        // then the workflow executes as expected
        assertEquals("666", output);

        // and an event is created in the database
        List<EventModel> events = MaestroService.getWorkflowEvents("example-simple-workflow-id");
        assertEquals(1, events.size());
        assertEquals("example-simple-workflow-id", events.get(0).workflowId());
        assertEquals("666", events.get(0).input());
        assertEquals("\"666\"", events.get(0).output());
        assertEquals("execute", events.get(0).functionName());
        assertEquals("ExampleSimpleWorkflowImpl", events.get(0).className());
        assertEquals(Category.WORKFLOW, events.get(0).category());

    }

    @Test
    void testSimpleWorkflowWithSignal() throws Exception {
        // given a running postgres container
        Assertions.assertTrue(postgresqlContainer.isRunning());

        // and a configured maestro
        Maestro maestro = MaestroService.builder()
                .configureDataSource(postgresqlContainer.getUsername(), postgresqlContainer.getPassword(), postgresqlContainer.getJdbcUrl())
                .build();
        maestro.registerWorkflowImplementationTypes(ExampleWorkflowWithSignalImpl.class);

        // when we create a workflow
        String workflowId = "example-with-signal-id";
        ExampleWorkflowWithSignal exampleSimpleWorkflow = MaestroService.newWorkflow(ExampleWorkflowWithSignalImpl.class, new WorkflowOptions(workflowId));
        // and execute the workflow
        String output = exampleSimpleWorkflow.execute(777);

        // then the workflow returns null because it's waiting on a signal
        assertNull(output);

        // and 2 events are created in the database
        List<EventModel> events = MaestroService.getWorkflowEvents(workflowId);
        assertEquals(2, events.size());

        // and one of the events is a WORKFLOW event with no end time and a null output
        assertEquals(workflowId, events.get(0).workflowId());
        assertEquals("777", events.get(0).input());
        assertNull(events.get(0).output());
        assertEquals("execute", events.get(0).functionName());
        assertEquals("ExampleWorkflowWithSignalImpl", events.get(0).className());
        assertNull(events.get(0).endTimestamp());
        assertEquals(Category.WORKFLOW, events.get(0).category());

        // and one is an AWAIT event with no end time
        assertEquals(workflowId, events.get(1).workflowId());
        assertEquals(Category.AWAIT, events.get(1).category());
        assertNull(events.get(1).endTimestamp());

        // and when we send the signal
        exampleSimpleWorkflow.doContinue(true);

        // then there will eventually be 3 events
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> MaestroService.getWorkflowEvents(workflowId).size() == 3);
        events = MaestroService.getWorkflowEvents(workflowId);

        // and WORFLOW will be complete with the correct output
        assertNotNull(events.get(0).endTimestamp());
        assertEquals("\"777\"", events.get(0).output());

        // and AWAIT is complete
        assertNotNull(events.get(1).endTimestamp());

        // and SIGNAL is correct
        assertEquals(workflowId, events.get(2).workflowId());
        assertEquals(Category.SIGNAL, events.get(2).category());
        assertEquals("true", events.get(2).input());


    }


    @Test
    void testWorkflowWithActivity() throws Exception {
        // given a running postgres container
        Assertions.assertTrue(postgresqlContainer.isRunning());

        // and a configured maestro
        Maestro maestro = MaestroService.builder()
                .configureDataSource(postgresqlContainer.getUsername(), postgresqlContainer.getPassword(), postgresqlContainer.getJdbcUrl())
                .build();
        maestro.registerWorkflowImplementationTypes(ExampleWorkflowWithActivityImpl.class);
        maestro.registerActivity(new ExampleMathActivityImpl());

        // when we create a workflow
        String workflowId = "example-workflow-with-activity-id";
        ExampleWorkflowWithActivity exampleWorkflowWithActivity = MaestroService.newWorkflow(ExampleWorkflowWithActivityImpl.class, new WorkflowOptions(workflowId));
        // and execute the workflow
        ExampleWorkflowWithActivity.ExampleWorkflowWithActivityParam param = new ExampleWorkflowWithActivity.ExampleWorkflowWithActivityParam(100, 2L, 20L);
        String output = exampleWorkflowWithActivity.execute(param);

        // then the workflow executes as expected
        // 100 x 2 - 20 = 180
        assertEquals("180", output);

        // and an event is created in the database
        List<EventModel> events = MaestroService.getWorkflowEvents(workflowId);
        assertEquals(3, events.size()); // 1 workflow and 2 activity is 3 total
        assertEquals(workflowId, events.get(0).workflowId());
        assertEquals("""
                {"startWith":100,"multiplyBy":2,"subtractBy":20}""", events.get(0).input());
        assertEquals("\"180\"", events.get(0).output());
        assertEquals("execute", events.get(0).functionName());
        assertEquals("ExampleWorkflowWithActivityImpl", events.get(0).className());
        assertNotNull(events.get(0).startTimestamp());
        assertNotNull(events.get(0).endTimestamp());
        assertEquals(Category.WORKFLOW, events.get(0).category());

        // and there are 2 activity events
        assertEquals(Category.ACTIVITY, events.get(1).category());
        assertEquals("multiply", events.get(1).functionName());

        assertEquals(Category.ACTIVITY, events.get(2).category());
        assertEquals("subtract", events.get(2).functionName());



    }


}
