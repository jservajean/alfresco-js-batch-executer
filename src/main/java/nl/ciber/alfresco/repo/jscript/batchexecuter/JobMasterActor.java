package nl.ciber.alfresco.repo.jscript.batchexecuter;

import akka.actor.*;
import akka.japi.Procedure;
import akka.routing.RoundRobinRouter;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;

import static nl.ciber.alfresco.repo.jscript.batchexecuter.Workers.BatchProcessorWorker;

/**
 * Actor which manages execution of a job.
 *
 * @author Bulat Yaminov
 */
public class JobMasterActor<T> extends UntypedActor {

    private static final Log logger = LogFactory.getLog(JobMasterActor.class);

    private final String jobId;
    private final BatchProcessWorkProvider<T> workProvider;
    private final ScriptBatchExecuter sbe;
    private final ActorRef workerRouter;
    private int batchesCount;

    public JobMasterActor(String jobId, int threads, BatchProcessWorkProvider<T> workProvider,
                          final BatchProcessorWorker<T> worker, ScriptBatchExecuter sbe) {
        this.jobId = jobId;
        this.workProvider = workProvider;
        this.sbe = sbe;
        workerRouter = this.getContext().actorOf(new Props(new UntypedActorFactory() {
            @Override
            public Actor create() {
                return new ProcessBatchActor<>(worker);
            }
        }).withRouter(
                new RoundRobinRouter(threads)), "workerRouter");
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof Execute) {

            logger.debug("starting executing a job");

            getContext().become(executing);

            batchesCount = 0;
            Collection<T> batch;
            while (true) {
                batch = workProvider.getNextWork();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                batchesCount++;
                logger.debug(String.format("feeding a batch of %d items to router of actors, %d batches fed so far",
                        batch.size(), batchesCount));
                workerRouter.tell(new ProcessBatchActor.Process<>(batch), getSelf());
            }

            if (batchesCount == 0) {
                // Premature finish
                logger.debug("there were no items to process, finishing");
                onFinished();
            }

        } else {
            unhandled(o);
        }
    }

    Procedure<Object> executing = new Procedure<Object>() {
        @Override
        public void apply(Object o) {
            if (o instanceof ProcessBatchActor.Finished) {

                batchesCount--;
                logger.debug(String.format("received result for a batch, %d batches still processing",
                        batchesCount));

                if (batchesCount == 0) {
                    onFinished();
                }

            } else if (o instanceof Cancel) {

                logger.debug("canceling the job");
                getContext().stop(getSelf());

            } else {
                unhandled(o);
            }
        }
    };

    private void onFinished() {
        logger.debug("finishing the job and stopping the master actor");
        sbe.jobFinished(jobId);
        getContext().unbecome();
        getContext().stop(getSelf());
    }


    /* Messages */

    public static class Execute {}
    public static class Cancel {}
}
