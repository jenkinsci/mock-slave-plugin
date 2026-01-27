package org.jenkinci.plugins.mock_slave;

import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.util.Collections;
import jenkins.security.MasterToSlaveCallable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MockSlaveLauncherTest {

    @TempDir
    private File tmp;

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @SuppressWarnings("deprecation")
    @Test
    void launch() throws Exception {
        ComputerLauncher launcher = new MockSlaveLauncher(25, 16 * 1024);
        DumbSlave slave = new DumbSlave("dummy", "dummy", tmp.getAbsolutePath(), "1", Node.Mode.NORMAL, "", launcher, RetentionStrategy.NOOP, Collections.emptyList());
        r.jenkins.addNode(slave);
        r.waitOnline(slave);
        assertEquals(43, slave.getChannel().call(new TestCallable()).intValue());
    }
    private static class TestCallable extends MasterToSlaveCallable<Integer,Error> {
        @Override
        public Integer call() throws Error {
            return 43;
        }
    }

}
