package nl.ciber.alfresco.repo.jscript.batchexecuter;

import akka.actor.UntypedActor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;

import static nl.ciber.alfresco.repo.jscript.batchexecuter.Workers.BatchProcessorWorker;

/**
 * Actor which processes one batch of work items, managing one transaction.
 *
 * @author Bulat Yaminov
 */
public class ProcessBatchActor<T> extends UntypedActor {

    private static final Log logger = LogFactory.getLog(ProcessBatchActor.class);

    private final BatchProcessorWorker<T> worker;

    public ProcessBatchActor(BatchProcessorWorker<T> worker) {
        this.worker = worker;
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof Process) {
            @SuppressWarnings("unchecked")
            Collection<T> data = ((Process<T>) o).getData();
            try {
                logger.debug("processing batch: " + data);

                worker.process(data);

                getSender().tell(new Finished());

            } catch (Throwable throwable) {
                throw new Exception(throwable);
            }
        }
    }


    /* Messages */

    public static class Process<T> {
        private Collection<T> data;

        public Process(Collection<T> data) {
            this.data = data;
        }

        public Collection<T> getData() {
            return data;
        }
    }

    public static class Finished {}
}
