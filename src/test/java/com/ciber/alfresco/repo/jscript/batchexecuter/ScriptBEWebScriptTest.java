package com.ciber.alfresco.repo.jscript.batchexecuter;

import org.alfresco.repo.processor.BaseProcessor;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.mozilla.javascript.Function;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.TestWebScriptServer;
import org.springframework.extensions.webscripts.TestWebScriptServer.GetRequest;
import org.springframework.extensions.webscripts.TestWebScriptServer.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link com.ciber.alfresco.repo.jscript.batchexecuter.ScriptBatchExecuter}.
 * Focuses on API webscripts.
 *
 * @author Bulat Yaminov
 */
public class ScriptBEWebScriptTest extends BaseWebScriptTest {

    private ScriptBatchExecuter realBatchExecuter;
    private MockBatchExecuter mockBatchExecuter;
    private BaseProcessor scriptProcessor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        realBatchExecuter = (ScriptBatchExecuter) this.getServer().getApplicationContext().getBean("batchExecuterScript");
        scriptProcessor = (BaseProcessor) this.getServer().getApplicationContext().getBean("javaScriptProcessor");
        mockBatchExecuter = new MockBatchExecuter();
        scriptProcessor.registerProcessorExtension(mockBatchExecuter);
        this.setDefaultRunAs("admin");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        scriptProcessor.registerProcessorExtension(realBatchExecuter);
    }

    public void testJobIsDisplayed() throws Exception {
        String url = "/ciber/batch-executer/jobs";
        Response resp = sendRequest(new GetRequest(url), Status.STATUS_OK);
        String response = resp.getContentAsString();
        assertContains(mockBatchExecuter.ID, response);
        assertContains(mockBatchExecuter.NAME, response);
        assertContains(mockBatchExecuter.NODE_FUNCTION, response);
        assertContains(mockBatchExecuter.BATCH_SIZE, response);
        assertContains(mockBatchExecuter.THREADS, response);
        assertContains(mockBatchExecuter.STATUS, response);
        assertContains(mockBatchExecuter.DISABLE_RULES, response);
    }

    public void testJobIsCanceled() throws Exception {
        String url = "/ciber/batch-executer/jobs/theId";
        sendRequest(new TestWebScriptServer.DeleteRequest(url), Status.STATUS_OK);
        assertEquals(1, mockBatchExecuter.canceledJobIds.size());
        assertEquals("theId", mockBatchExecuter.canceledJobIds.get(0));
    }

    private static void assertContains(Object str, String inText) {
        assertTrue(String.format("Test '%s' expected in '%s'", str, inText), inText.contains(String.valueOf(str)));
    }


    private class MockBatchExecuter extends ScriptBatchExecuter {

        public final String ID = "mock-id";
        public final String NAME = "mock-name";
        public final int THREADS = 2;
        public final int BATCH_SIZE = 2;
        public final boolean DISABLE_RULES = true;
        public final String NODE_FUNCTION = "function(node) {}";
        public final String BATCH_FUNCTION = "function(batch) {}";
        public final BatchJobParameters.Status STATUS = BatchJobParameters.Status.RUNNING;

        List<String> canceledJobIds = new ArrayList<>();

        @Override
        public synchronized boolean cancelJob(String jobId) {
            canceledJobIds.add(jobId);
            return true;
        }

        @Override
        public String getExtensionName() {
            return "batchExecuter";
        }

        @Override
        public Collection<BatchJobParameters> getCurrentJobs() {
            BatchJobParameters job = new BatchJobParameters() {

                @Override
                public String getId() {
                    return ID;
                }

                @Override
                public String getName() {
                    return NAME;
                }

                @Override
                public int getThreads() {
                    return THREADS;
                }

                @Override
                public int getBatchSize() {
                    return BATCH_SIZE;
                }

                @Override
                public boolean getDisableRules() {
                    return DISABLE_RULES;
                }

                @Override
                public String getOnNodeFunction() {
                    return NODE_FUNCTION;
                }

                @Override
                public String getOnBatchFunction() {
                    return BATCH_FUNCTION;
                }

                @Override
                public Status getStatus() {
                    return STATUS;
                }

                @Override
                public Function getOnNode() {
                    return null;
                }

                @Override
                public Function getOnBatch() {
                    return null;
                }

                @Override
                public void setName(String id) {}

                @Override
                public void setThreads(int threads) {}

                @Override
                public void setBatchSize(int batchSize) {}

                @Override
                public void setDisableRules(boolean disableRules) {}

                @Override
                public void setOnNode(Function onNode) {}

                @Override
                public void setOnBatch(Function onBatch) {}

                @Override
                public void setId(String id) {}

                @Override
                protected void setStatus(Status status) {}
            };
            return Collections.singleton(job);
        }

    }

}
