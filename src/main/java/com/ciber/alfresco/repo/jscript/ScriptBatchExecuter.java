package com.ciber.alfresco.repo.jscript;

import com.ciber.alfresco.repo.jscript.batchexecuter.WorkProviders;
import com.ciber.alfresco.repo.jscript.batchexecuter.Workers;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.Map;

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

    private static final String PARAM_ITEMS = "items";
    private static final String PARAM_ROOT = "root";
    private static final String PARAM_BATCH_SIZE = "batchSize";
    private static final String PARAM_THREADS = "threads";
    private static final String PARAM_ON_NODE = "onNode";
    private static final String PARAM_ON_BATCH = "onBatch";
    private static final String PARAM_DISABLE_RULES = "disableRules";

    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int DEFAULT_THREADS = 4;

    private ServiceRegistry sr;
    private ApplicationContext applicationContext;

    public String processArray(Object params) {
        /* Parse custom parameters */
        Map<String, Object> paramsMap = getParametersMap(params);
        final List<Object> items = RhinoUtils.getArray(paramsMap, PARAM_ITEMS);
        if (items == null) {
            throw new IllegalArgumentException(PARAM_ITEMS + " must be specified and be an array");
        }
        String jobName = generateJobName(items.size() + "-items");
        return doProcess(paramsMap, WorkProviders.CollectionWorkProviderFactory.getInstance(), items, jobName);
    }

    public String processFolderRecursively(Object params) {
        /* Parse custom parameters */
        Map<String, Object> paramsMap = getParametersMap(params);
        final ScriptNode root = RhinoUtils.getScriptNode(paramsMap, PARAM_ROOT);
        if (root == null) {
            throw new IllegalArgumentException(PARAM_ROOT + " must be specified and be a node");
        }
        String jobName = generateJobName(getName(root.getNodeRef()) + "-folder");
        return doProcess(paramsMap,
                new WorkProviders.FolderBrowsingWorkProviderFactory(sr, getScope(), logger),
                root.getNodeRef(), jobName);
    }

    private <T> String doProcess(Map<String, Object> paramsMap,
                                 WorkProviders.NodeOrBatchWorkProviderFactory<T> workFactory,
                                 T data, String jobName) {
        /* Parse common parameters */
        int batchSize = RhinoUtils.getInteger(paramsMap, PARAM_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        int threads = RhinoUtils.getInteger(paramsMap, PARAM_THREADS, DEFAULT_THREADS);
        boolean disableRules = RhinoUtils.getBoolean(paramsMap, PARAM_DISABLE_RULES, false);

        final Function onNode = RhinoUtils.getFunction(paramsMap, PARAM_ON_NODE);
        final Function onBatch = RhinoUtils.getFunction(paramsMap, PARAM_ON_BATCH);
        if (onNode == null && onBatch == null) {
            throw new IllegalArgumentException("one of " + PARAM_ON_NODE + " or " + PARAM_ON_BATCH +
                    " function is required");
        }
        if (onNode != null && onBatch != null) {
            throw new IllegalArgumentException("only one of " + PARAM_ON_NODE + " or " + PARAM_ON_BATCH +
                    " function can be specified");
        }

        /* Process items */
        final Scriptable cachedScope = getScope();
        final String user = AuthenticationUtil.getFullyAuthenticatedUser();

        RetryingTransactionHelper rth = sr.getTransactionService().getRetryingTransactionHelper();
//        rth.setMaxRetries(3);
//        rth.setMinRetryWaitMs(1000);

        if (onNode != null) {

            // Let the BatchProcessor do the batching
            BatchProcessor<Object> processor = new BatchProcessor<>(jobName, rth,
                    workFactory.newNodesWorkProvider(data),
                    threads, batchSize, applicationContext, logger, 1000);
            Workers.ProcessNodeWorker worker = new Workers.ProcessNodeWorker(onNode, cachedScope,
                    user, disableRules, sr.getRuleService(), logger, this);
            logger.info(String.format("Starting batch processor '%s' to process %s",
                    jobName, workFactory.describe(data)));
            processor.process(worker, true);

        } else {

            // Split into batches here so that onBatch function can process them
            BatchProcessor<List<Object>> processor = new BatchProcessor<>(jobName, rth,
                    workFactory.newBatchesWorkProvider(data, batchSize),
                    threads, 1, applicationContext, logger, 1);
            Workers.ProcessBatchWorker worker = new Workers.ProcessBatchWorker(onBatch, cachedScope,
                    user, disableRules, sr.getRuleService(), logger, this);
            logger.info(String.format("Starting batch processor '%s' to process %s with batch function",
                    jobName, workFactory.describe(data)));
            processor.process(worker, true);
        }

        return jobName;
    }

    private Map<String, Object> getParametersMap(Object params) {
        if (!(params instanceof ScriptableObject)) {
            throw new IllegalArgumentException("first parameter must be an object");
        }
        return RhinoUtils.convertToMap((ScriptableObject) params);
    }

    private String generateJobName(String description) {
        if (description == null)
            description = "";
        return String.format("BatchExecuter-%s_%s", RandomStringUtils.randomAlphabetic(4).toLowerCase(),
                description.substring(0, Math.min(20, description.length())));
    }

    private String getName(NodeRef nodeRef) {
        if (sr.getNodeService().exists(nodeRef)) {
            return (String) sr.getNodeService().getProperty(nodeRef, ContentModel.PROP_NAME);
        }
        return "non-existing node";
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.sr = serviceRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
