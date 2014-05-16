package nl.ciber.alfresco.repo.jscript.batchexecuter;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.logging.Log;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

import java.util.*;

/**
 * Container class for all work providers used by
 * {@link nl.ciber.alfresco.repo.jscript.batchexecuter.ScriptBatchExecuter}.
 *
 * @author Bulat Yaminov
 */
public class WorkProviders {

    public static class CollectionWorkProvider<T> implements BatchProcessWorkProvider<T> {

        private int itemsSize;
        private Iterator<T> iterator;
        private int batchSize;

        public CollectionWorkProvider(Collection<T> items, int batchSize) {
            this.itemsSize = items.size();
            this.batchSize = batchSize;
            this.iterator = items.iterator();
        }

        @Override
        public int getTotalEstimatedWorkSize() {
            return itemsSize;
        }

        @Override
        public Collection<T> getNextWork() {
            List<T> batch = new ArrayList<>(batchSize);
            while (iterator.hasNext() && batch.size() < batchSize) {
                batch.add(iterator.next());
            }
            return batch;
        }
    }

    public static class FolderBrowsingWorkProvider implements BatchProcessWorkProvider<Object> {

        private FolderBrowser browser;
        private int batchSize;

        protected FolderBrowsingWorkProvider(NodeRef root, int batchSize, ServiceRegistry sr,
                    Log logger, Scriptable scope) {
            this.browser = new FolderBrowser(root, sr, logger, scope);
            this.batchSize = batchSize;
        }

        @Override
        public int getTotalEstimatedWorkSize() {
            return -1;
        }

        /** Returns just one batch wrapped in a collection */
        @Override
        public Collection<Object> getNextWork() {
            List<Object> batch = new ArrayList<>();
            while (!browser.stack.isEmpty() && batch.size() < batchSize) {
                batch.add(browser.convertToJS(browser.pop()));
            }
            return batch;
        }
    }

    private static class FolderBrowser {

        private Stack<NodeRef> stack = new Stack<>();

        private ServiceRegistry sr;
        private NodeService ns;
        private DictionaryService ds;
        private Log logger;
        private Scriptable scope;
        private String runAsUser;

        private FolderBrowser(NodeRef root, ServiceRegistry sr,
                              Log logger, Scriptable scope) {
            this.sr = sr;
            this.ns = sr.getNodeService();
            this.ds = sr.getDictionaryService();
            this.logger = logger;
            this.scope = scope;
            this.runAsUser = sr.getAuthenticationService().getCurrentUserName();
            stack.push(root);
        }

        protected NativeJavaObject convertToJS(NodeRef node) {
            ScriptNode scriptNode = new ScriptNode(node, sr, scope);
            return new NativeJavaObject(scope, scriptNode, ScriptNode.class);
        }

        protected NodeRef pop() {
            if (stack.isEmpty()) {
                return null;
            }
            NodeRef head = stack.pop();
            AuthenticationUtil.setRunAsUser(runAsUser);
            if (ds.isSubClass(ns.getType(head), ContentModel.TYPE_FOLDER)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("fetching children of " + head);
                }
                List<ChildAssociationRef> children = ns.getChildAssocs(
                        head, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
                // Add to stack so that first child would appear as the head
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i).getChildRef());
                }
            }
            return head;
        }
    }
}