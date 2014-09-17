/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinci.plugins.mock_slave;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Simple cloud that creates mock slaves on demand.
 */
public final class MockCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(MockCloud.class.getName());

    private static final AtomicInteger counter = new AtomicInteger();

    static {
        // Make things happen more quickly so that we can test it interactively.
        NodeProvisioner.NodeProvisionerInvoker.INITIALDELAY = 1000;
        NodeProvisioner.NodeProvisionerInvoker.RECURRENCEPERIOD = 1000;
    }

    public final Node.Mode mode;
    public final int numExecutors;
    public final String labelString;

    @DataBoundConstructor public MockCloud(String name, Node.Mode mode, int numExecutors, String labelString) {
        super(name);
        this.mode = mode;
        this.numExecutors = numExecutors;
        this.labelString = labelString;
    }

    @Override public boolean canProvision(Label label) {
        LOGGER.log(Level.FINE, "checking whether we can provision {0}", label);
        return label == null ? mode == Node.Mode.NORMAL : label.matches(Label.parse(labelString));
    }

    @Override public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        Collection<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();
        while (excessWorkload > 0) {
            final int cnt = counter.incrementAndGet();
            r.add(new NodeProvisioner.PlannedNode("Mock Slave #" + cnt, Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                public Node call() throws Exception {
                    return new MockCloudSlave(cnt, mode, numExecutors, labelString);
                }
            }), numExecutors));
            excessWorkload -= numExecutors;
        }
        LOGGER.log(Level.FINE, "planning to provision {0} slaves", r.size());
        return r;
    }

    @Extension public static final class DescriptorImpl extends Descriptor<Cloud> {

        @Override public String getDisplayName() {
            return "Mock Cloud";
        }

    }

    private static final class MockCloudSlave extends AbstractCloudSlave implements EphemeralNode {

        MockCloudSlave(int cnt, Node.Mode mode, int numExecutors, String labelString) throws FormException, IOException {
            super("mock-slave-" + cnt, "Mock Slave", Util.createTempDir().getAbsolutePath(), numExecutors, mode, labelString, new MockSlaveLauncher(0, 0), numExecutors == 1 ? new OnceRetentionStrategy() : new CloudRetentionStrategy(1), Collections.<NodeProperty<?>>emptyList());
        }

        @Override public AbstractCloudComputer<?> createComputer() {
            return new MockCloudComputer(this);
        }

        @Override protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
            // need do nothing
        }

        @Override public Node asNode() {
            return this;
        }

    }

    private static final class MockCloudComputer extends AbstractCloudComputer<MockCloudSlave> {

        MockCloudComputer(MockCloudSlave slave) {
            super(slave);
        }

    }

    private static final class OnceRetentionStrategy extends RetentionStrategy<AbstractCloudComputer<?>> implements ExecutorListener {

        @Override public void start(AbstractCloudComputer<?> c) {
            c.connect(false);
        }

        @Override public long check(AbstractCloudComputer<?> c) {
            // Should be unnecessary, but just in case:
            if (c.isIdle() && !c.isAcceptingTasks()) {
                kill(c);
            }
            return 1;
        }

        @Override public void taskAccepted(Executor executor, Queue.Task task) {}

        @Override public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
            done(executor);
        }

        @Override public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
            done(executor);
        }

        private void done(Executor executor) {
            AbstractCloudComputer<?> c = (AbstractCloudComputer) executor.getOwner();
            c.setAcceptingTasks(false); // just in case
            // Best to kill them off ASAP; otherwise NodeProvisioner does nothing until ComputerRetentionWork has run, causing poor throughput:
            kill(c);
            // TODO calling NodeProvisioner.suggestReviewNow here does not seem to help push things along at all
        }

        private void kill(AbstractCloudComputer<?> c) {
            try {
                c.getNode().terminate();
            } catch (InterruptedException x) {
                LOGGER.log(Level.WARNING, null, x);
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }

    }

}
