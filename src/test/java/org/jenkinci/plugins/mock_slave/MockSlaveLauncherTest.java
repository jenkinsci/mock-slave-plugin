package org.jenkinci.plugins.mock_slave;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import jenkins.security.MasterToSlaveCallable;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class MockSlaveLauncherTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Test public void launch() throws Exception {
        ComputerLauncher launcher = new MockSlaveLauncher(25, 16 * 1024);
        DumbSlave slave = new DumbSlave("dummy", "dummy", tmp.getRoot().getAbsolutePath(), "1", Node.Mode.NORMAL, "", launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        j.jenkins.addNode(slave);
        final CountDownLatch latch = new CountDownLatch(1);
        ComputerListener waiter = new ComputerListener() {
            @Override public void onOnline(Computer C, TaskListener t) {
                latch.countDown();
                unregister();
            }
        };
        waiter.register();
        latch.await();
        assertEquals(43, slave.getChannel().call(new TestCallable()).intValue());
    }
    private static class TestCallable extends MasterToSlaveCallable<Integer,Error> {
        @Override public Integer call() throws Error {
            return 43;
        }
    }

}
