package nl.ciber.alfresco.repo.jscript.batchexecuter;

import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

import javax.transaction.UserTransaction;
import java.util.Collection;

/**
 * Container class for all worker implementations used by
 * {@link nl.ciber.alfresco.repo.jscript.batchexecuter.ScriptBatchExecuter}.
 *
 * @author Bulat Yaminov
 */
public class Workers {

    /**
     * A batch processor worker which processes items in batches.
     */
    public interface BatchProcessorWorker<T> {

        /**
         * Processes a batch of items.
         * @param batch the batch.
         * @throws Exception in case error happens, it is responsibility of the caller to retry.
         */
        public void process(Collection<T> batch) throws Exception;

    }

    private abstract static class BaseBatchProcessorWorker<T> implements BatchProcessorWorker<T> {

        protected Scriptable scope;
        private String userName;
        private boolean disableRules;
        private RuleService ruleService;
        private TransactionService transactionService;
        protected Log logger;
        private BaseScopableProcessorExtension scopable;

        protected Function processFunction;

        private BaseBatchProcessorWorker(Function processFunction, Scriptable scope,
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
        public final void process(Collection<T> batch) throws Exception {
            if (logger.isTraceEnabled()) {
                logger.trace("beforeProcess: entering context");
            }
            Context.enter();
            scopable.setScope(scope);
            AuthenticationUtil.setRunAsUser(userName);
            if (disableRules) {
                ruleService.disableRules();
            }

            UserTransaction trx = transactionService.getUserTransaction();
            try {
                trx.begin();
                doProcess(batch);
                trx.commit();
            } catch (Exception e) {
                trx.rollback();
                throw e;
            } finally {

                if (logger.isTraceEnabled()) {
                    logger.trace("afterProcess: exiting context");
                }
                Context.exit();
                if (disableRules) {
                    ruleService.enableRules();
                }

            }
        }

        protected abstract void doProcess(Collection<T> batch) throws Exception;
    }

    public static class ApplyBatchFunctionWorker extends BaseBatchProcessorWorker<Object> {
        public ApplyBatchFunctionWorker(Function processBatchFunction, Scriptable scope, String userName,
                                        boolean disableRules, RuleService ruleService,
                                        TransactionService transactionService, Log logger,
                                        BaseScopableProcessorExtension scopable) {
            super(processBatchFunction, scope, userName, disableRules, ruleService, transactionService,
                    logger, scopable);
        }

        @Override
        protected void doProcess(Collection<Object> batch) throws Exception {
            Scriptable itemsArray = Context.getCurrentContext().newArray(scope, batch.toArray());
            Object resultArray = processFunction.call(Context.getCurrentContext(),
                    scope, scope, new Object[]{ itemsArray });
            if (logger.isTraceEnabled() && resultArray instanceof NativeArray) {
                logger.trace(String.format("call on batch gave %d results out of %d",
                        ((NativeArray) resultArray).getIds().length, batch.size()));
            }
        }
    }

    public static class ApplyNodeFunctionWorker extends BaseBatchProcessorWorker<Object> {
        public ApplyNodeFunctionWorker(Function processNodeFunction, Scriptable scope, String userName,
                                       boolean disableRules, RuleService ruleService,
                                       TransactionService transactionService, Log logger,
                                       BaseScopableProcessorExtension scopable) {
            super(processNodeFunction, scope, userName, disableRules, ruleService, transactionService,
                    logger, scopable);
        }

        @Override
        protected void doProcess(Collection<Object> batch) throws Exception {
            for (Object entry : batch) {
                Object result = processFunction.call(Context.getCurrentContext(),
                        scope, scope, new Object[]{ entry });
                logger.trace(String.format("call on %s %s", entry, result == null ? "skipped" : "done"));
            }
        }
    }

}

