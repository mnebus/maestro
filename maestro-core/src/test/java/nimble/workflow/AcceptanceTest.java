package nimble.workflow;

import nimble.workflow.internal.persistence.WorkflowRepository;
import nimble.workflow.model.Workflow;
import nimble.workflow.util.ExampleService;
import nimble.workflow.util.ExampleWorkflow;
import nimble.workflow.util.ExampleWorkflowWithNestedActivities;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class AcceptanceTest {

    @Container
    private static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer()
            .withDatabaseName("test-database")
            .withUsername("test-user")
            .withPassword("test-password");

    NimbleWorkflow nimble;

    @BeforeEach
    void setup() {
        this.nimble = NimbleWorkflow.builder()
                .dataSource("test-user", "test-password", postgresqlContainer.getJdbcUrl())
                .registerWorkflowDependencies(new ExampleService())
                .start();
    }

    @AfterEach
    void destroy() {
        this.nimble.stop();
    }

    @Test
    public void repositoryTest() throws Exception {
        WorkflowRepository repository = NimbleWorkflow.repository;

        repository.newWorkflowScheduled("test-workflow-id", ExampleWorkflow.class, 666);
        Workflow workflowModel = repository.getWorkflow("test-workflow-id");
        System.out.println("found the model %s".formatted(workflowModel));
    }


    @Test
    public void testNestedActivities() throws Exception {

        String workflowId = "nested-activity-test";

        nimble.executor().runWorkflow(ExampleWorkflowWithNestedActivities.class, "900", workflowId);

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .until(() -> nimble.executor().getWorkflowOutput(workflowId, Integer.class) != null);

        int output = nimble.executor().getWorkflowOutput(workflowId, Integer.class);
        assertEquals(900, output);
    }

    @Test
    public void test() throws Exception {

        String workflowId = "test-workflow";
        nimble.executor().runWorkflow(ExampleWorkflow.class, 666, workflowId);

        nimble.executor().signalWorkflow(ExampleWorkflow.class, workflowId, "OkToResume", true);

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .until(() -> "666asdf".equals(nimble.executor().getWorkflowOutput(workflowId, String.class)));
        System.out.println("done: " + nimble.executor().getWorkflowOutput(workflowId, String.class));
    }


}
