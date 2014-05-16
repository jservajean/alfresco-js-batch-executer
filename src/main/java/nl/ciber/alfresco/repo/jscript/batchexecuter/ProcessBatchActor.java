package nl.ciber.alfresco.repo.jscript.batchexecuter;

import akka.actor.UntypedActor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;

/**
 * Actor which processes one batch of work items, managing one transaction.
 *
 * @author Bulat Yaminov
 */
public class ProcessBatchActor<T> extends UntypedActor {

    private static final Log logger = LogFactory.getLog(ProcessBatchActor.class);

    private final Workers.CancellableWorker<T> worker;

    public ProcessBatchActor(Workers.CancellableWorker<T> worker) {
        this.worker = worker;
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof Process) {
            @SuppressWarnings("unchecked")
            Collection<T> singletonList = ((Process<T>) o).getData();
            if (singletonList != null && !singletonList.isEmpty()) {
                if (singletonList.size() > 1) {
                    logger.warn(String.format("More than one (%d) data object found, " +
                            "processing only the first one: %s", singletonList.size(), singletonList));
                }
                T data = singletonList.iterator().next();
                try {
                    logger.debug("processing data: " + data);

                    worker.beforeProcess();
                    worker.process(data);
                    worker.afterProcess();

                    getSender().tell(new Finished());

                } catch (Throwable throwable) {
                    throw new Exception(throwable);
                }
            } else {
                logger.warn("Empty or null work item received: " + singletonList);
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
