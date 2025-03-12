package nimble.workflow;

import nimble.workflow.internal.persistence.WorkflowRepository;
import nimble.workflow.model.Workflow;
import nimble.workflow.util.ExampleService;
import nimble.workflow.util.ExampleWorkflow;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

@Testcontainers
public class AcceptanceTest {

    @Container
    private static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer()
            .withDatabaseName("test-database")
            .withUsername("test-user")
            .withPassword("test-password");

    @Test
    public void repositoryTest() throws Exception {
        NimbleWorkflow workflow = NimbleWorkflow.builder()
                .dataSource("test-user", "test-password", postgresqlContainer.getJdbcUrl())
                .registerWorkflowDependencies(new ExampleService())
                .start();

        WorkflowRepository repository = NimbleWorkflow.repository;

        repository.newWorkflowScheduled("test-workflow-id", ExampleWorkflow.class, 666);
        Workflow workflowModel = repository.getWorkflow("test-workflow-id");
        System.out.println("found the model %s".formatted(workflowModel));
    }

    @Test
    public void test() throws Exception {
        NimbleWorkflow workflow = NimbleWorkflow.builder()
                .dataSource("test-user", "test-password", postgresqlContainer.getJdbcUrl())
                .registerWorkflowDependencies(new ExampleService())
                .start();

        String workflowId = "test-workflow";
        workflow.executor().runWorkflow(ExampleWorkflow.class, 666, workflowId);

        workflow.executor().signalWorkflow(ExampleWorkflow.class, workflowId, "OkToResume", true);

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .until(() -> "666asdf".equals(workflow.executor().getWorkflowOutput(workflowId, String.class)));
        System.out.println("done: " + workflow.executor().getWorkflowOutput(workflowId, String.class));
    }


}
