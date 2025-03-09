package lucidity.maestro.engine;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lucidity.maestro.engine.api.Maestro;
import lucidity.maestro.engine.api.workflow.WorkflowOptions;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.config.Initializer;
import lucidity.maestro.engine.internal.handler.Await;
import lucidity.maestro.engine.internal.repo.EventRepo;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.function.Supplier;

public class MaestroService {

    private static final Logger logger = LoggerFactory.getLogger(MaestroService.class);
    private static MaestroImpl serviceInstance;

    public static void await(Supplier<Boolean> condition) {
        serviceInstance.workflowActions().await(condition);
    }

    public static void sleep(Duration duration) {
        serviceInstance.workflowActions().sleep(duration);
    }

    public static Maestro getInstance() {
        return serviceInstance;
    }

    public static <T> T newWorkflow(Class<T> clazz, WorkflowOptions options) {
        return serviceInstance.newWorkflow(clazz, options);
    }

    public static MaestroServiceBuilder builder() {
        return new MaestroServiceBuilder();
    }

    public static class MaestroServiceBuilder {

        private DataSource dataSource;

        public MaestroServiceBuilder configureDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public MaestroServiceBuilder configureDataSource(String username, String password, String url) {
            this.dataSource = initializeDataSource(username, password, url);
            return this;
        }

        public Maestro build() {
            runDatabaseMigration(this.dataSource);

            EventRepo eventRepo = new EventRepo(this.dataSource);
            MaestroImpl m = new MaestroImpl(eventRepo, this.dataSource);
            Initializer.initialize(m, eventRepo);
            serviceInstance = m;
            return m;
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

    }
}
