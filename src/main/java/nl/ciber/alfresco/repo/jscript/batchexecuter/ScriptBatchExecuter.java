package nl.ciber.alfresco.repo.jscript.batchexecuter;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Scriptable;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import static nl.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters.ProcessArrayJobParameters;
import static nl.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters.ProcessFolderJobParameters;
import static nl.ciber.alfresco.repo.jscript.batchexecuter.WorkProviders.CollectionWorkProvider;
import static nl.ciber.alfresco.repo.jscript.batchexecuter.WorkProviders.FolderBrowsingWorkProvider;
import static nl.ciber.alfresco.repo.jscript.batchexecuter.Workers.*;

/**
 * JavaScript object which helps execute big data changes in Alfresco.
 *
 * The object allows providing a set of nodes to process and a function to run,
 * then splits nodes in batches and executes each batch in a separate transaction
 * and multiple threads.
 *
 * @author Bulat Yaminov
 */
@SuppressWarnings("UnusedDeclaration")
public class ScriptBatchExecuter extends BaseScopableProcessorExtension {

    private static final Log logger = LogFactory.getLog(ScriptBatchExecuter.class);

    private ServiceRegistry sr;

    private ActorSystemWrapper actorSystem;

    private static ConcurrentHashMap<String, BatchJobParameters> runningJobs = new ConcurrentHashMap<>(10);
    private static ConcurrentHashMap<String, ActorRef> runningMasterActors = new ConcurrentHashMap<>(10);

    /**
     * Starts processing an array of objects, applying a function to each object or batch of objects
     * within the array.
     *
     * This is a blocking call.
     *
     * @param params processing params, with array stored as 'items' property. See
     * {@link nl.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters} for all parameters.
     * @return job ID.
     */
    public String processArray(Object params) {
        ProcessArrayJobParameters job = BatchJobParameters.parseArrayParameters(params);
        return doProcess(job, new CollectionWorkProvider<>(job.getItems(), job.getBatchSize()));
    }

    /**
     * Starts processing a folder and its children recursively, applying a function to each
     * node or batch of nodes. Both folders and documents are included.
     *
     * This is a blocking call.
     *
     * @param params processing params, with the folder ScriptNode stored as 'root' property. See
     * {@link nl.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters} for all parameters.
     * @return job ID.
     */
    public String processFolderRecursively(Object params) {
        ProcessFolderJobParameters job = BatchJobParameters.parseFolderParameters(params);
        return doProcess(job, new FolderBrowsingWorkProvider(
                job.getRoot().getNodeRef(), job.getBatchSize(), sr, logger, getScope()));
    }

    /**
     * Get the list of currently executing jobs.
     *
     * @return collection of jobs being executed.
     */
    public Collection<BatchJobParameters> getCurrentJobs() {
        return runningJobs.values();
    }

    /**
     * Cancels a job by given job ID. Any batches being already fed to the processor
     * will be finished, but no new batches will be started.
     *
     * @param jobId job ID
     * @return true if job existed by given ID and was cancelled.
     * False if job was already finished or never existed.
     */
    public synchronized boolean cancelJob(String jobId) {
        if (jobId == null) {
            return false;
        }
        ActorRef master = runningMasterActors.get(jobId);
        if (master == null) {
            return false;
        }
        master.tell(new JobMasterActor.Cancel());
        BatchJobParameters job = runningJobs.get(jobId);
        if (job != null) {
            job.setStatus(BatchJobParameters.Status.CANCELED);
        }
        return true;
    }

    private String doProcess(final BatchJobParameters job,
                                 final BatchProcessWorkProvider<Object> workProvider) {
        /* Process items */
        runningJobs.put(job.getId(), job);

        final Scriptable cachedScope = getScope();
        final String user = AuthenticationUtil.getFullyAuthenticatedUser();

        final BatchProcessorWorker<Object> worker;

        if (job.getOnNode() != null) {
            worker = new ApplyNodeFunctionWorker(job.getOnNode(), cachedScope,
                    user, job.getDisableRules(), sr.getRuleService(), sr.getTransactionService(),
                    logger, this);
        } else {
            worker = new ApplyBatchFunctionWorker(job.getOnBatch(), cachedScope,
                    user, job.getDisableRules(), sr.getRuleService(), sr.getTransactionService(),
                    logger, this);
        }

        UntypedActorFactory factory = new UntypedActorFactory() {
            @Override
            public Actor create() {
                return new JobMasterActor<>(job.getId(), job.getThreads(), workProvider, worker,
                        ScriptBatchExecuter.this);
            }
        };


        ActorRef master = actorSystem.getActorSystem().actorOf(new Props(factory), job.getId());
        runningMasterActors.put(job.getId(), master);

        logger.info(String.format("Starting batch processor '%s'%s",
                job.getName(), job.getOnNode() == null ? " with batch function" : ""));

        job.setStatus(BatchJobParameters.Status.RUNNING);
        master.tell(new JobMasterActor.Execute());

        return job.getName();
    }

    protected void jobFinished(String jobId) {
        BatchJobParameters job = runningJobs.get(jobId);
        if (job != null && job.getStatus() != BatchJobParameters.Status.CANCELED) {
            job.setStatus(BatchJobParameters.Status.FINISHED);
        }
        ScriptBatchExecuter.runningJobs.remove(jobId);
        ScriptBatchExecuter.runningMasterActors.remove(jobId);
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.sr = serviceRegistry;
    }

    public void setActorSystem(ActorSystemWrapper actorSystem) {
        this.actorSystem = actorSystem;
    }
}
