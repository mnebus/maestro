package lucidity.maestro.engine.api.workflow;

import lucidity.maestro.engine.MaestroService;
import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.handler.Await;
import lucidity.maestro.engine.internal.handler.Sleep;
import lucidity.maestro.engine.internal.repo.EventRepo;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.function.Supplier;

public interface Workflow {

    void await(Supplier<Boolean> condition);

    void sleep(Duration duration);

    static class WorkflowImpl implements Workflow {

        private Sleep sleep;
        private Await await;

        public WorkflowImpl(MaestroImpl maestroImpl, EventRepo eventRepo, DataSource dataSource) {
            this.sleep = new Sleep(maestroImpl, eventRepo, dataSource);
            this.await = new Await(maestroImpl,eventRepo);
        }

        @Override
        public void await(Supplier<Boolean> condition) {
            this.await.await(condition);
        }

        @Override
        public void sleep(Duration duration) {
            this.sleep.sleep(duration);
        }
    }
}
