package com.ciber.alfresco.repo.jscript;

import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.service.transaction.TransactionService;
import org.mozilla.javascript.Context;

/**
 * Proxy to the TransactionService to use from JavaScript in Alfresco. Allows controlling
 * transactions even from non-secure repository-based scripts.
 *
 * @author Bulat Yaminov
 */
@SuppressWarnings("UnusedDeclaration")
public class ScriptTransactionService extends BaseScopableProcessorExtension {

    private TransactionService transactionService;

    public Object getUserTransaction() {
        return Context.javaToJS(transactionService.getUserTransaction(), getScope());
    }

    public Object getUserTransaction(boolean readOnly) {
        return Context.javaToJS(transactionService.getUserTransaction(readOnly), getScope());
    }

    public Object getNonPropagatingUserTransaction() {
        return Context.javaToJS(transactionService.getNonPropagatingUserTransaction(), getScope());
    }

    public Object getNonPropagatingUserTransaction(boolean readOnly) {
        return Context.javaToJS(transactionService.getNonPropagatingUserTransaction(readOnly), getScope());
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
}
