package lucidity.maestro.engine.internal.config;

import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.http.Server;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.worker.TimedOutWorkflowWorker;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

public class Initializer {

    private static final AtomicBoolean configured = new AtomicBoolean(false);

    private static TimedOutWorkflowWorker timedOutWorkflowWorker;

    public static void initialize(MaestroImpl maestroImpl, EventRepo eventRepo) {
        if (configured.get()) return;

        timedOutWorkflowWorker = new TimedOutWorkflowWorker(maestroImpl, eventRepo);
        timedOutWorkflowWorker.startPoll();


        Server server = new Server(eventRepo);
        server.serve();

        configured.set(true);
    }
}
