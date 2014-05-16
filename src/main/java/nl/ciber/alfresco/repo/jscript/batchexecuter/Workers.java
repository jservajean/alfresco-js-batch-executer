package nl.ciber.alfresco.repo.jscript.batchexecuter;

import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.mozilla.javascript.*;

import javax.transaction.UserTransaction;
import java.util.List;

/**
 * Container class for all worker implementations used by
 * {@link nl.ciber.alfresco.repo.jscript.batchexecuter.ScriptBatchExecuter}.
 *
 * @author Bulat Yaminov
 */
public class Workers {

    /**
     * A batch processor worker which can be canceled. When canceled it will skip any
     * processing requests.
     */
    public interface CancellableWorker<T> extends BatchProcessor.BatchProcessWorker<T> {
        /**
         * Notifies this worker to skip processing of any entries.
         * @return true if this worker was not canceled before.
         */
        boolean cancel();
    }

    private abstract static class BaseProcessWorker<T> extends BatchProcessor.BatchProcessWorkerAdaptor<T>
                                                        implements CancellableWorker<T> {

        protected Scriptable scope;
        private String userName;
        private boolean disableRules;
        private RuleService ruleService;
        private TransactionService transactionService;
        protected Log logger;
        private BaseScopableProcessorExtension scopable;
        private boolean canceled;

        protected Function processFunction;

        private BaseProcessWorker(Function processFunction, Scriptable scope,
                                  String userName, boolean disableRules,
                                  RuleService ruleService,
                                  TransactionService transactionService, Log logger,
                                  BaseScopableProcessorExtension scopable) {
            this.processFunction = processFunction;
            this.scope = scope;
            this.userName = userName;
            this.disableRules = disableRules;
            this.ruleService = ruleService;
            this.transactionService = transactionService;
            this.logger = logger;
            this.scopable = scopable;
        }

        @Override
        public void beforeProcess() throws Throwable {
            if (logger.isTraceEnabled()) {
                logger.trace("beforeProcess: entering context");
            }
            Context.enter();
            scopable.setScope(scope);
            AuthenticationUtil.setRunAsUser(userName);
            if (disableRules) {
                ruleService.disableRules();
            }
        }

        @Override
        public void afterProcess() throws Throwable {
            if (logger.isTraceEnabled()) {
                logger.trace("afterProcess: exiting context");
            }
            Context.exit();
            if (disableRules) {
                ruleService.enableRules();
            }
        }

        @Override
        public final void process(T entry) throws Throwable {
            if (!canceled) {
                UserTransaction trx = transactionService.getUserTransaction();
                try {
                    trx.begin();
                    doProcess(entry);
                    trx.commit();
                } catch (Throwable t) {
                    trx.rollback();
                    throw t;
                }
            }
        }

        public synchronized boolean cancel() {
            if (!canceled) {
                canceled = true;
                return true;
            }
            return false;
        }

        protected abstract void doProcess(T entry) throws Throwable;
    }

    @Deprecated
    public static class ProcessNodeWorker extends BaseProcessWorker<Object> {
        public ProcessNodeWorker(Function processFunction, Scriptable scope, String userName,
                                  boolean disableRules, RuleService ruleService, Log logger,
                                  BaseScopableProcessorExtension scopable) {
            super(processFunction, scope, userName, disableRules, ruleService, null, logger, scopable);
        }

        @Override
        protected void doProcess(Object entry) throws Throwable {
            Object result = processFunction.call(Context.getCurrentContext(),
                    scope, scope, new Object[]{ entry });
            if (logger.isTraceEnabled()) {
                logger.trace(String.format("call on %s %s", entry, result == null ? "skipped" : "done"));
            }
        }

        @Override
        public String getIdentifier(Object entry) {
            if (entry instanceof NativeJavaObject) {
                Object o = ((NativeJavaObject) entry).unwrap();
                if (o instanceof ScriptNode) {
                    return String.format("%s (%s)", ((ScriptNode) o).getName(), ((ScriptNode) o).getNodeRef());
                }
            }
            return super.getIdentifier(entry);
        }
    }

    public static class ProcessBatchWorker extends BaseProcessWorker<List<Object>> {
        public ProcessBatchWorker(Function processBatchFunction, Scriptable scope, String userName,
                                   boolean disableRules, RuleService ruleService,
                                   TransactionService transactionService, Log logger,
                                   BaseScopableProcessorExtension scopable) {
            super(processBatchFunction, scope, userName, disableRules, ruleService, transactionService,
                    logger, scopable);
        }

        @Override
        protected void doProcess(List<Object> entry) throws Throwable {
            Scriptable itemsArray = Context.getCurrentContext().newArray(scope, entry.toArray());
            Object resultArray = processFunction.call(Context.getCurrentContext(),
                    scope, scope, new Object[]{ itemsArray });
            if (logger.isTraceEnabled() && resultArray instanceof NativeArray) {
                logger.trace(String.format("call on batch gave %d results out of %d",
                        ((NativeArray) resultArray).getIds().length, entry.size()));
            }
        }
    }

    public static class ProcessBatchWithOnNodeFunctionWorker extends BaseProcessWorker<List<Object>> {
        public ProcessBatchWithOnNodeFunctionWorker(Function processNodeFunction, Scriptable scope, String userName,
                                   boolean disableRules, RuleService ruleService,
                                   TransactionService transactionService, Log logger,
                                   BaseScopableProcessorExtension scopable) {
            super(processNodeFunction, scope, userName, disableRules, ruleService, transactionService,
                    logger, scopable);
        }

        @Override
        protected void doProcess(List<Object> entries) throws Throwable {
            for (Object entry : entries) {
                Object result = processFunction.call(Context.getCurrentContext(),
                        scope, scope, new Object[]{ entry });
                logger.trace(String.format("call on %s %s", entry, result == null ? "skipped" : "done"));
            }
        }
    }

}

