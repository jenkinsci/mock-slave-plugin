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
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodeProvisioner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Simple cloud that creates mock slaves on demand.
 */
public final class MockCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(MockCloud.class.getName());

    private static final AtomicInteger counter = new AtomicInteger();

    static {
        // JENKINS-24752: make things happen more quickly so that we can test it interactively.
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
                @Override public Node call() throws Exception {
                    return new MockCloudSlave("mock-slave-" + cnt, mode, numExecutors, labelString);
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

    private static final class MockCloudSlave extends AbstractCloudSlave {

        MockCloudSlave(String slaveName, Node.Mode mode, int numExecutors, String labelString) throws FormException, IOException {
            super(slaveName, "Mock Slave", MockSlave.root(slaveName), numExecutors, mode, labelString, new MockSlaveLauncher(0, 0), numExecutors == 1 ? new OnceRetentionStrategy(1) : new CloudRetentionStrategy(1), Collections.<NodeProperty<?>>emptyList());
        }

        @Override public AbstractCloudComputer<?> createComputer() {
            return new MockCloudComputer(this);
        }

        @Override protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
            // need do nothing
        }

    }

    private static final class MockCloudComputer extends AbstractCloudComputer<MockCloudSlave> {

        MockCloudComputer(MockCloudSlave slave) {
            super(slave);
        }

    }

}
