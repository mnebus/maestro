package nimble.workflow;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.logging.LogLevel;
import com.github.kagkarlsson.scheduler.task.Task;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nimble.workflow.api.WorkflowExecutor;
import nimble.workflow.api.WorkflowFunctions;
import nimble.workflow.internal.SchedulerConfig;
import nimble.workflow.internal.persistence.WorkflowRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NimbleWorkflow {

    public static WorkflowRepository repository;
    private final WorkflowExecutor workflowExecutor;

    private NimbleWorkflow(WorkflowExecutor workflowExecutor) {
        this.workflowExecutor = workflowExecutor;
    }

    public static NimbusServiceBuilder builder() {
        return new NimbusServiceBuilder();
    }

    public WorkflowExecutor executor() {
        return this.workflowExecutor;
    }

    public static class NimbusServiceBuilder {

        private final Set<Object> workflowDependencies = new HashSet<>();
        private DataSource dataSource;

        private NimbusServiceBuilder() {
        }

        private static void runDatabaseMigration(DataSource dataSource) {
            MigrateResult migrate = Flyway.configure()
                    .baselineVersion("0")
                    .baselineOnMigrate(true)
                    .dataSource(dataSource)
                    .load()
                    .migrate();
        }

        private static HikariDataSource initializeDataSource(String username, String password, String url) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(10);

            // setting min idle prevents the datasource from eagerly creating max pool size
            config.setMinimumIdle(2);
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            return new HikariDataSource(config);
        }

        public NimbusServiceBuilder dataSource(String username, String password, String url) {
            this.dataSource = initializeDataSource(username, password, url);
            return this;
        }

        public NimbusServiceBuilder registerWorkflowDependencies(Object... objects) {
            workflowDependencies.addAll(Arrays.asList(objects));
            return this;
        }

        public NimbleWorkflow start() {
            runDatabaseMigration(this.dataSource);

            SchedulerConfig.initialize(this.workflowDependencies);

            Scheduler scheduler = initializeScheduler(this.dataSource,
                    SchedulerConfig.START_WORKFLOW_TASK,
                    SchedulerConfig.SIGNAL_WORKFLOW_TASK,
                    SchedulerConfig.COMPLETE_SLEEP_TASK,
                    SchedulerConfig.WAIT_FOR_CONDITION_TASK);

            WorkflowExecutor executor = new WorkflowExecutor(scheduler);
            NimbleWorkflow.repository = new WorkflowRepository(Jdbi.create(this.dataSource));
            WorkflowFunctions.initialize(scheduler);
            //TODO -- null check this.dataSource
            return new NimbleWorkflow(executor, this.dataSource);
        }

        private Scheduler initializeScheduler(DataSource dataSource, Task... tasks) {
            Scheduler scheduler = Scheduler
                    .create(dataSource, tasks)
                    .pollingInterval(Duration.ofSeconds(1))
                    .registerShutdownHook()
                    .failureLogging(LogLevel.INFO, false)
                    .build();

            scheduler.start();

            return scheduler;
        }
    }
}
