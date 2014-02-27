package com.ciber.alfresco.repo.jscript;

import com.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Tests {@link ScriptBatchExecuter}. Focuses mainly on jobs management functionality.
 *
 * @author Bulat Yaminov
 */
public class ScriptBEJobManagementTest extends BaseScriptingTest {

//    private static final Log logger = LogFactory.getLog(BaseScriptingTest.class);

    private static ScriptBatchExecuter batchExecuter;

    @BeforeClass
    public static void initContext() {
        batchExecuter = (ScriptBatchExecuter) ctx.getBean("batchesScript");
    }

    @Test
    public void jobsListCanBeFetched() throws InterruptedException {
        executeWithModelNonBlocking(
                "batchExecuter.processFolderRecursively({\n" +
                "    root: companyhome,\n" +
                "    batchSize: 5,\n" +
                "    onNode: function(node) {}\n" +
                "});\n"
        );
        Collection<BatchJobParameters> jobs = batchExecuter.getCurrentJobs();
        assertTrue(jobs.size() >= 1);
        BatchJobParameters job = getJobByNameContains(jobs, "Company Home");
        assertNotNull(job);
        String function = job.getOnNodeFunction();
        assertTrue("Expected something like 'function(node) {}', but found: [" + function + "]",
                function.matches("\\s*function\\s*\\(node\\)\\s*\\{\\s*\\}\\s*"));
        assertEquals(5, job.getBatchSize());
        assertTrue(job instanceof BatchJobParameters.ProcessFolderJobParameters);

        Thread.sleep(2000);
        // Job should have finished within a second or so
        assertEquals(null, getJobByNameContains(batchExecuter.getCurrentJobs(), "Company Home"));
    }

    private BatchJobParameters getJobByNameContains(Collection<BatchJobParameters> jobs, String text) {
        BatchJobParameters job = null;
        for (BatchJobParameters jobParameters : jobs) {
            if (jobParameters.getName().contains(text)) {
                job = jobParameters;
                break;
            }
        }
        return job;
    }

    @Test
    public void jobCanBeStopped() throws Exception {
        // TODO: maybe can work with default testHome
        NodeRef tests2 = ns.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS, "Tests2");
        if (tests2 != null) {
            ns.deleteNode(tests2);
        }
        tests2 = sr.getFileFolderService().create(companyHome, "Tests2", ContentModel.TYPE_FOLDER).getNodeRef();
        try {
            final int maxCreateCount = 50;
            executeWithModelNonBlocking(
                    "var array = [];\n" +
                    "for (var i = 0; i < " + maxCreateCount + "; i++) { array[i] = i; }\n" +
                    "batchExecuter.processArray({\n" +
                    "    items: array,\n" +
                    "    batchSize: 5,\n" +
                    "    threads: 2,\n" +
                    "    onNode: function(node) {\n" +
                    "        var file = companyhome.childByNamePath('Tests2').createFile('test-doc-' + node + '.bin');\n" +
                    "    }\n" +
                    "});\n"
            );

            Collection<BatchJobParameters> jobs = batchExecuter.getCurrentJobs();
            assertEquals(1, jobs.size());
            BatchJobParameters job = jobs.iterator().next();

            batchExecuter.cancelJob(job.getId());
            // Wait for unfinished batches to complete
            Thread.sleep(2000);

            int createdCount = sr.getFileFolderService().listFiles(tests2).size();

            assertTrue("Some files were created", createdCount > 0);
            assertTrue("Job was canceled", createdCount < maxCreateCount);
            assertTrue("Started batches were completed", createdCount % 5 == 0);

        } finally {
            // Wait for the whole job to complete
            if (ns.exists(tests2)) {
                ns.deleteNode(tests2);
            }
        }
    }

    @Test
    public void memoryAllocationDoesNotIncreaseWhileExecuting() {
        // TODO: implement
    }

}
