package com.ciber.alfresco.repo.jscript;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.ApplicationContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static com.ibm.icu.impl.Assert.fail;
import static org.junit.Assert.*;

/**
 * Tests {@link ScriptBatchExecuter}.
 *
 * @author Bulat Yaminov
 */
public class ScriptBatchExecuterTest {

    private static final Log logger = LogFactory.getLog(ScriptBatchExecuterTest.class);

    private static final String FUNCTION_RENAME_NODE =
                "function(node) {\n" +
            "        if (node.isDocument) {\n" +
            "            logger.info('renaming ' + node.name);\n" +
            "            node.properties['cm:name'] = 'changed-' + node.name;\n" +
            "            node.save();\n" +
            "            return node;\n" +
            "        }\n" +
            "    }";

    private static final String FUNCTION_RENAME_BATCH =
                "function(batch) {\n" +
            "        for (var i = 0; i < batch.length; i++) {\n" +
            "            var node = batch[i];\n" +
            "            if (node.isDocument) {\n" +
            "                logger.info('renaming ' + node.name);\n" +
            "                node.properties['cm:name'] = 'changed-' + node.name;\n" +
            "                node.save();\n" +
            "            }\n" +
            "        }\n" +
            "        return batch;\n" +
            "    }";

    public static final String FUNCTION_NULL = "null";

    private static ServiceRegistry sr;
    private static NodeService ns;
    private static NodeRef testHome;

    @BeforeClass
    public static void initAppContext() {
        ApplicationContextHelper.setUseLazyLoading(false);
        ApplicationContextHelper.setNoAutoStart(true);
        ApplicationContext applicationContext = ApplicationContextHelper.getApplicationContext(
                new String[]{"classpath:alfresco/application-context.xml"});
        ns = (NodeService) applicationContext.getBean("NodeService");
        sr = (ServiceRegistry) applicationContext.getBean("ServiceRegistry");
        AuthenticationUtil.setFullyAuthenticatedUser("admin");
        NodeRef companyHome = sr.getNodeLocatorService().getNode("companyhome", null, null);
        testHome = ns.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS, "Tests");
        if (testHome == null) {
            logger.info("Creating folder /Company Home/Tests/");
            testHome = sr.getFileFolderService().create(companyHome, "Tests", ContentModel.TYPE_FOLDER).getNodeRef();
        }
        logger.info("Application Context loaded");
    }

    @After
    public void clearTestFolder() {
        if (ns.exists(testHome)) {
            List<FileInfo> children = sr.getFileFolderService().list(testHome);
            for (FileInfo child : children) {
                ns.deleteNode(child.getNodeRef());
            }
        }
    }

    @Test
    public void jsObjectExists() {
        final String script =
                "batchExecuter.processArray !== undefined;";
        Object result = execute(script);
        assertTrue((Boolean) result);
    }

    @Test
    public void processesListByNodes() {
        testProcessesList(FUNCTION_RENAME_NODE, FUNCTION_NULL);
    }

    @Test
    public void processesListByBatches() {
        testProcessesList("null", FUNCTION_RENAME_BATCH);
    }

    private void testProcessesList(String onNodeJS, String onBatchJS) {
        final String n1 = "node1.txt";
        final String n2 = "node2.txt";
        NodeRef d1 = createTestDocument(n1);
        NodeRef d2 = createTestDocument(n2);

        final String script = String.format(
                        "var d1 = search.findNode('%3$s');\n" +
                        "var d2 = search.findNode('%4$s');\n" +
                        "batchExecuter.processArray({\n" +
                        "   items: [d1, d2],\n" +
                        "   batchSize: 1,\n" +
                        "   threads: 2,\n" +
                        "   onNode: %1$s,\n" +
                        "   onBatch: %2$s\n" +
                        "});\n" +
                        "var d1n = search.findNode('%3$s');\n" +
                        "var d2n = search.findNode('%4$s');\n" +
                        "[d1n.name, d2n.name];\n",
                onNodeJS, onBatchJS, d1, d2);

        Object result = execute(script);
        assertTrue(result instanceof List);
        // Verify that names were not changed in the same thread
        assertArrayEquals(new String[] {n1, n2}, ((List) result).toArray());

        // Verify that names changed after a while
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertEquals("changed-" + n1, ns.getProperty(d1, ContentModel.PROP_NAME));
        assertEquals("changed-" + n2, ns.getProperty(d2, ContentModel.PROP_NAME));
    }

    @Test
    public void processesFolderByNodes() {
        testProcessesFolder(FUNCTION_RENAME_NODE, FUNCTION_NULL);
    }

    @Test
    public void processesFolderByBatches() {
        testProcessesFolder(FUNCTION_NULL, FUNCTION_RENAME_BATCH);
    }

    private void testProcessesFolder(String onNodeJS, String onBatchJS) {
        final String n1 = "doc1.txt";
        final String n2 = "doc2.txt";
        final String n3 = "doc3.txt";
        NodeRef f1 = sr.getFileFolderService().create(testHome, "folder1", ContentModel.TYPE_FOLDER).getNodeRef();
        NodeRef d1 = createTestDocument(n1, f1);
        NodeRef f2 = sr.getFileFolderService().create(f1, "folder2", ContentModel.TYPE_FOLDER).getNodeRef();
        NodeRef d2 = createTestDocument(n2, f2);
        NodeRef d3 = createTestDocument(n3, f2);

        final String script = String.format(
                        "var f1 = search.findNode('%3$s');\n" +
                        "batchExecuter.processFolderRecursively({\n" +
                        "    root: f1,\n" +
                        "    batchSize: 1,\n" +
                        "    threads: 2,\n" +
                        "    onNode: %1$s,\n" +
                        "    onBatch: %2$s,\n" +
                        "    disableRules: true\n" +
                        "});\n" +
                        "var d1 = search.findNode('%4$s');\n" +
                        "var d2 = search.findNode('%5$s');\n" +
                        "var d3 = search.findNode('%6$s');\n" +
                        "[d1.name, d2.name, d3.name];\n",
                onNodeJS, onBatchJS, f1, d1, d2, d3);

        Object result = execute(script);
        assertArrayEquals(new String[]{n1, n2, n3}, ((List) result).toArray());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertEquals("changed-" + n1, ns.getProperty(d1, ContentModel.PROP_NAME));
        assertEquals("changed-" + n2, ns.getProperty(d2, ContentModel.PROP_NAME));
        assertEquals("changed-" + n3, ns.getProperty(d3, ContentModel.PROP_NAME));
    }

    @Test
    public void preservesScope() {
        final String n1 = "node1.txt";
        final String n2 = "node2.txt";
        NodeRef d1 = createTestDocument(n1);
        NodeRef d2 = createTestDocument(n2);

        final String script = String.format(
                "var CONSTANT = 'changed-';\n" +
                "var d1 = search.findNode('%1$s');\n" +
                "var d2 = search.findNode('%2$s');\n" +
                "batchExecuter.processArray({\n" +
                "    items: [d1, d2],\n" +
                "    batchSize: 1,\n" +
                "    threads: 2,\n" +
                "    onNode: function(node) {\n" +
                "        var newName = CONSTANT + node.name;\n" +
                "        logger.info('CONSTANT = ' + CONSTANT);\n" +
                "        logger.info('d1.created = ' + d1.properties['cm:created']);\n" +
                "        node.properties['cm:name'] = newName;\n" +
                "        node.save();\n" +
                "    }\n" +
                "});",
            d1, d2);

        execute(script);

        // Verify that names changed after a while
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertEquals("changed-" + n1, ns.getProperty(d1, ContentModel.PROP_NAME));
        assertEquals("changed-" + n2, ns.getProperty(d2, ContentModel.PROP_NAME));
    }

    @Test
    public void defaultModelIsAvailable() {
        final String script =
                "logger.info('companyhome: ' + companyhome);\n" +
                "batchExecuter.processArray({\n" +
                "    items: ['test'],\n" +
                "    batchSize: 1,\n" +
                "    threads: 2,\n" +
                "    onNode: function(node) {\n" +
                "        logger.info('companyhome: ' + companyhome.name);\n" +
                "        logger.info('userhome: ' + userhome.name);\n" +
                "        var testHome = search.luceneSearch('PATH:\"/app:company_home/cm:Tests\"')[0];\n" +
                "        logger.info('testHome found: ' + testHome.name);\n" +
                "        logger.info('current user: ' + person.properties['cm:userName']);\n" +
                "    }\n" +
                "});\n" +
                "companyhome;";

        executeWithModel(script);
        // No exceptions must be thrown
    }

    @Test
    public void jobNameContainsArraySize() {
        Object result = execute(
                "batchExecuter.processArray({\n" +
                "    items: [1, 2, 3, 4, 5],\n" +
                "    onNode: function(node) {}\n" +
                "});\n"
        );
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("5"));
    }

    @Test
    public void jobNameContainsFolderName() {
        Object result = execute(String.format(
                "batchExecuter.processFolderRecursively({\n" +
                "    root: search.findNode('%s'),\n" +
                "    onNode: function(node) {}\n" +
                "});\n",
            testHome));
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("Tests"));
    }

    @Test
    public void handlesJavaArray() {
        Object result = executeWithModel(
                "var node = companyhome.childByNamePath('Tests').createFile('test.txt')\n" +
                "node.content = 'a,b,c,d,e';\n" +
                "node.save();\n" +
                "node.reset();\n" +
                "batchExecuter.processArray({\n" +
                "    items: node.content.split(','),\n" +
                "    onNode: function(node) {}\n" +
                "});\n"
        );
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("5"));
    }

    @Test
    public void memoryAllocationDoesNotIncreaseWhileExecuting() {
        // TODO: implement
    }

    @Test
    public void jobsListCanBeFetched() {
        // TODO: implement
    }

    @Test
    public void jobCanBeStopped() {
        // TODO: implement
    }


    private Object execute(String script) {
        return sr.getScriptService().executeScriptString(script, null);
    }

    private Object executeWithModel(String script) {
        NodeRef companyHome = sr.getSearchService().selectNodes(
                sr.getNodeService().getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE),
                "/app:company_home",
                null,
                sr.getNamespaceService(),
                false).get(0);
        String userName = sr.getAuthenticationService().getCurrentUserName();
        NodeRef person = sr.getPersonService().getPerson(userName);
        NodeRef userHome = (NodeRef) sr.getNodeService().getProperty(person, ContentModel.PROP_HOMEFOLDER);

        Map<String, Object> model = sr.getScriptService().buildDefaultModel(person, companyHome,
                userHome, null, null, null);

        return sr.getScriptService().executeScriptString(script, model);
    }

    private NodeRef createTestDocument(String name) {
        return createTestDocument(name, testHome);
    }

    private NodeRef createTestDocument(String name, NodeRef parent) {
        return sr.getFileFolderService().create(parent, name, ContentModel.TYPE_CONTENT).getNodeRef();
    }

}
