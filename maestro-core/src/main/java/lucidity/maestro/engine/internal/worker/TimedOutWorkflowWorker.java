package lucidity.maestro.engine.internal.worker;

import lucidity.maestro.engine.internal.MaestroImpl;
import lucidity.maestro.engine.internal.entity.EventEntity;
import lucidity.maestro.engine.internal.repo.EventRepo;
import lucidity.maestro.engine.internal.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimedOutWorkflowWorker {
    private static final Logger logger = LoggerFactory.getLogger(TimedOutWorkflowWorker.class);
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MaestroImpl maestroImpl;
    private final EventRepo eventRepo;

    public TimedOutWorkflowWorker(MaestroImpl maestroImpl, EventRepo eventRepo) {

        this.maestroImpl = maestroImpl;
        this.eventRepo = eventRepo;
    }


    public void startPoll() {
        if (started.get()) return;

        executor.submit(this::poll);

        started.set(true);
    }

    public void poll() {
        while (true) {
            List<EventEntity> timedOutEvents = eventRepo.getTimedOutEvents();
            timedOutEvents.forEach(this::logAndReplay);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void logAndReplay(EventEntity workflow) {
        logger.info("replaying workflow with id: {}", workflow.workflowId());
        maestroImpl.replayWorkflow(workflow);
    }
}
