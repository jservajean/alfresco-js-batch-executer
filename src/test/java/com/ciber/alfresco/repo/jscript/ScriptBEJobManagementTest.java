package com.ciber.alfresco.repo.jscript;

import com.ciber.alfresco.repo.jscript.batchexecuter.BatchJobParameters;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link ScriptBatchExecuter}. Focuses mainly on jobs management functionality.
 *
 * @author Bulat Yaminov
 */
public class ScriptBEJobManagementTest extends BaseScriptingTest {

    private static ScriptBatchExecuter batchExecuter;

    @BeforeClass
    public static void initContext() {
        batchExecuter = (ScriptBatchExecuter) ctx.getBean("batchesScript");
    }

    @Test
    public void memoryAllocationDoesNotIncreaseWhileExecuting() {
        // TODO: implement
    }

    @Test
    public void jobsListCanBeFetched() {
        Object result = executeWithModel(
                "batchExecuter.processFolderRecursively({\n" +
                        "    root: companyhome,\n" +
                        "    onNode: function(node) {}\n" +
                        "});\n"
        );
        String jobId = String.valueOf(result);
        List<BatchJobParameters> jobs = batchExecuter.getCurrentJobs();
        assertEquals(1, jobs.size());
        assertEquals(jobId, jobs.get(0).getId());
    }

    @Test
    public void jobCanBeStopped() {
        // TODO: implement
    }


}
