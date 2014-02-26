package com.ciber.alfresco.repo.jscript;

import com.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters;
import com.ciber.alfresco.repo.jscript.batchexecuter.WorkProviders;
import com.ciber.alfresco.repo.jscript.batchexecuter.Workers;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Scriptable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collections;
import java.util.List;

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
public class ScriptBatchExecuter extends BaseScopableProcessorExtension implements ApplicationContextAware {

    private static final Log logger = LogFactory.getLog(ScriptBatchExecuter.class);

    private ServiceRegistry sr;
    private ApplicationContext applicationContext;

    public String processArray(Object params) {
        BatchJobParameters.ProcessArrayJobParameters job = BatchJobParameters.parseArrayParameters(params);
        return doProcess(job, WorkProviders.CollectionWorkProviderFactory.getInstance(), job.getItems());
    }

    public String processFolderRecursively(Object params) {
        BatchJobParameters.ProcessFolderJobParameters job = BatchJobParameters.parseFolderParameters(params);
        return doProcess(job,
                new WorkProviders.FolderBrowsingWorkProviderFactory(sr, getScope(), logger),
                job.getRoot().getNodeRef());
    }

    public List<BatchJobParameters> getCurrentJobs() {
        // TODO: implement
        return Collections.emptyList();
    }

    private <T> String doProcess(BatchJobParameters job,
                                 WorkProviders.NodeOrBatchWorkProviderFactory<T> workFactory,
                                 T data) {
        /* Process items */
        final Scriptable cachedScope = getScope();
        final String user = AuthenticationUtil.getFullyAuthenticatedUser();

        RetryingTransactionHelper rth = sr.getTransactionService().getRetryingTransactionHelper();

        if (job.getOnNode() != null) {

            // Let the BatchProcessor do the batching
            BatchProcessor<Object> processor = new BatchProcessor<>(job.getId(), rth,
                    workFactory.newNodesWorkProvider(data),
                    job.getThreads(), job.getBatchSize(), applicationContext, logger, 1000);
            Workers.ProcessNodeWorker worker = new Workers.ProcessNodeWorker(job.getOnNode(), cachedScope,
                    user, job.getDisableRules(), sr.getRuleService(), logger, this);
            logger.info(String.format("Starting batch processor '%s' to process %s",
                    job.getId(), workFactory.describe(data)));
            processor.process(worker, true);

        } else {

            // Split into batches here so that onBatch function can process them
            BatchProcessor<List<Object>> processor = new BatchProcessor<>(job.getId(), rth,
                    workFactory.newBatchesWorkProvider(data, job.getBatchSize()),
                    job.getThreads(), 1, applicationContext, logger, 1);
            Workers.ProcessBatchWorker worker = new Workers.ProcessBatchWorker(job.getOnBatch(), cachedScope,
                    user, job.getDisableRules(), sr.getRuleService(), logger, this);
            logger.info(String.format("Starting batch processor '%s' to process %s with batch function",
                    job.getId(), workFactory.describe(data)));
            processor.process(worker, true);
        }

        return job.getId();
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.sr = serviceRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
