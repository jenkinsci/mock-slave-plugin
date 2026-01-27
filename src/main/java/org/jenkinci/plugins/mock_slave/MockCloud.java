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
import hudson.Functions;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.queue.QueueListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.Listeners;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Simple cloud that creates mock slaves on demand.
 */
public final class MockCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(MockCloud.class.getName());

    @DataBoundSetter public Node.Mode mode = Node.Mode.NORMAL;
    private int numExecutors = 1; // field had a poor name
    private String labelString = ""; // field had a poor name
    private Boolean oneShot = true; // reading null for compatibility
    private boolean inbound;
    // TODO could also support WebSocket
    private int maximum;

    @DataBoundConstructor public MockCloud(String name) {
        super(name);
    }

    public String getLabels() {
        return labelString;
    }

    @DataBoundSetter public void setLabels(String labels) {
        labelString = Util.fixNull(labels);
    }

    public boolean getOneShot() {
        return oneShot;
    }

    @DataBoundSetter public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
    }

    public int getExecutors() {
        return numExecutors;
    }

    @DataBoundSetter public void setExecutors(int executors) {
        numExecutors = executors;
    }

    public boolean isInbound() {
        return inbound;
    }

    @DataBoundSetter public void setInbound(boolean inbound) {
        this.inbound = inbound;
    }

    public int getMaximum() {
        return maximum;
    }

    @DataBoundSetter public void setMaximum(int maximum) {
        this.maximum = maximum;
    }

    private Object readResolve() {
        if (oneShot == null) {
            oneShot = numExecutors == 1;
        }
        return this;
    }

    @Override public boolean canProvision(Cloud.CloudState state) {
        Label label = state.getLabel();
        LOGGER.fine(() -> "checking whether " + name + " can provision " + label);
        return label == null ? mode == Node.Mode.NORMAL : label.matches(Label.parse(labelString));
        // Not taking maximum into account, since this method is normally interpreted as corresponding to static configuration.
        // but see https://github.com/jenkinsci/jenkins/issues/20434
    }

    @Override public Collection<NodeProvisioner.PlannedNode> provision(CloudState state, int excessWorkload) {
        int originalExcessWorkload = excessWorkload;
        LOGGER.fine(() -> "name=" + name + " label=" + state.getLabel() + " additionalPlannedCapacity=" + state.getAdditionalPlannedCapacity() + " excessWorkload=" + originalExcessWorkload);
        Collection<NodeProvisioner.PlannedNode> r = new ArrayList<>();
        while (excessWorkload > 0) {
            if (maximum > 0) {
                long curr = Jenkins.get().getNodes().stream().filter(n -> n instanceof MockCloudSlave mcs && name.equals(mcs.cloudName)).count();
                if (curr >= maximum) {
                    int more = excessWorkload;
                    LOGGER.fine(() -> name + " already running " + curr + " agents ≥" + maximum + "; will not provision " + more + " more");
                    break;
                }
            }
            long cnt = ((DescriptorImpl) getDescriptor()).newNodeNumber();
            CompletableFuture<Node> future;
            try {
                MockCloudSlave agent = new MockCloudSlave(name, "mock-agent-" + cnt, inbound);
                agent.setNodeDescription("Mock agent #" + cnt);
                agent.setMode(mode);
                agent.setNumExecutors(numExecutors);
                agent.setLabelString(labelString);
                agent.setRetentionStrategy(oneShot ? new OnceRetentionStrategy(5) : new CloudRetentionStrategy(1));
                future = CompletableFuture.completedFuture(agent);
            } catch (IOException | Descriptor.FormException x) {
                future = CompletableFuture.failedFuture(x);
            }
            r.add(new NodeProvisioner.PlannedNode("Mock Agent #" + cnt, future, numExecutors));
            excessWorkload -= numExecutors;
        }
        LOGGER.fine(() -> name + " planning to provision " + r.size() + " agents");
        return r;
    }

    @Symbol("mock")
    @Extension public static final class DescriptorImpl extends Descriptor<Cloud> {

        private long counter;

        public DescriptorImpl() {
            load();
        }

        synchronized long newNodeNumber() {
            counter++;
            save();
            return counter;
        }

        @Restricted(DoNotUse.class) // for tests only
        public String nextAgentName() {
            return "mock-agent-" + (counter + 1);
        }

        @Override public String getDisplayName() {
            return "Mock Cloud";
        }

        public FormValidation doCheckOneShot(@QueryParameter int executors, @QueryParameter boolean oneShot) {
            if (oneShot && executors != 1) {
                return FormValidation.error("One-shot mode should only be used when agents have one executor apiece.");
            } else {
                return FormValidation.ok();
            }
        }

    }

    private static final class MockCloudSlave extends AbstractCloudSlave {

        final String cloudName;

        private MockCloudSlave(String cloudName, String slaveName, boolean inbound) throws FormException, IOException {
            super(slaveName, MockSlave.root(slaveName), inbound ? new MockInboundLauncher() : new MockSlaveLauncher(0, 0));
            this.cloudName = cloudName;
        }

        @Override public AbstractCloudComputer<?> createComputer() {
            return new MockCloudComputer(this);
        }

        @Override protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
            // need do nothing
        }

        @Extension public static final class DescriptorImpl extends SlaveDescriptor {

            @Override public boolean isInstantiable() {
                return false;
            }

        }

    }

    private static final class MockInboundLauncher extends JNLPLauncher {

        private transient Process proc;

        MockInboundLauncher() {}

        @Override public boolean isLaunchSupported() {
            return proc == null;
        }

        @Override public void launch(SlaveComputer computer, TaskListener listener) {
            LOGGER.fine(() -> "launching agent for " + computer.getName());
            try {
                File agentJar = new File(Jenkins.get().getRootDir(), "agent.jar");
                if (!agentJar.isFile()) {
                    FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), agentJar);
                }
                proc = new ProcessBuilder(
                        "java", "-jar", agentJar.getAbsolutePath(),
                        "-url", JenkinsLocationConfiguration.get().getUrl(),
                        "-name", computer.getName(),
                        "-secret", computer.getJnlpMac()).
                    redirectErrorStream(true).
                    start();
                new StreamCopyThread("I/O of " + computer.getName(), proc.getInputStream(), listener.getLogger()).start();
                Instant max = Instant.now().plus(Duration.ofSeconds(15));
                while (computer.isOffline() && Instant.now().isBefore(max)) {
                    Thread.sleep(100);
                }
            } catch (Exception x) {
                Functions.printStackTrace(x, listener.error("Failed to launch"));
            }
        }

        @Override public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
            LOGGER.fine(() -> "terminating agent for " + computer.getName());
            proc.destroy();
        }

    }

    private static final class MockCloudComputer extends AbstractCloudComputer<MockCloudSlave> {

        MockCloudComputer(MockCloudSlave slave) {
            super(slave);
        }

    }

    // Adapted from io.jenkins.plugins.kubernetes; TODO introduce to core with some sort of marker on Cloud:
    @Extension(ordinal = 100)
    public static class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {
        @Override public NodeProvisioner.StrategyDecision apply(NodeProvisioner.StrategyState strategyState) {
            final Label label = strategyState.getLabel();
            LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
            int availableCapacity = snapshot.getAvailableExecutors() + snapshot.getConnectingExecutors() + strategyState.getPlannedCapacitySnapshot() + strategyState.getAdditionalPlannedCapacity();
            int previousCapacity = availableCapacity;
            int currentDemand = snapshot.getQueueLength();
            LOGGER.log(Level.FINE, "Available capacity={0}, currentDemand={1}", new Object[] {availableCapacity, currentDemand});
            if (availableCapacity < currentDemand) {
                List<Cloud> jenkinsClouds = new ArrayList<>(Jenkins.get().clouds);
                Cloud.CloudState cloudState = new Cloud.CloudState(label, strategyState.getAdditionalPlannedCapacity());
                for (Cloud cloud : jenkinsClouds) {
                    int workloadToProvision = currentDemand - availableCapacity;
                    if (!(cloud instanceof MockCloud)) {
                        continue;
                    }
                    if (!cloud.canProvision(cloudState)) {
                        continue;
                    }
                    if (CloudProvisioningListener.all().stream().anyMatch(cl -> cl.canProvision(cloud, cloudState, workloadToProvision) != null)) {
                        continue;
                    }
                    Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(cloudState, workloadToProvision);
                    LOGGER.fine(() -> cloud.name + " planned " + plannedNodes.size() + " new nodes");
                    Listeners.notify(CloudProvisioningListener.class, true, cl -> cl.onStarted(cloud, strategyState.getLabel(), plannedNodes));
                    strategyState.recordPendingLaunches(plannedNodes);
                    availableCapacity += plannedNodes.size();
                    var ac = availableCapacity;
                    LOGGER.fine(() -> cloud.name + " after provisioning, available capacity: " + ac + "; current demand: " + currentDemand);
                    if (availableCapacity >= currentDemand) { // added vs. kubernetes-plugin version; see note in canProvision
                        break;
                    }
                }
            }
            if (availableCapacity > previousCapacity && label != null) {
                LOGGER.fine("Suggesting NodeProvisioner review");
                Timer.get().schedule(label.nodeProvisioner::suggestReviewNow, 1L, TimeUnit.SECONDS);
            }
            if (availableCapacity >= currentDemand) {
                LOGGER.fine("Provisioning completed");
                return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
            } else {
                LOGGER.fine("Provisioning not complete, consulting remaining strategies");
                return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
            }
        }
        @Extension public static class FastProvisioning extends QueueListener {
            @Override public void onEnterBuildable(Queue.BuildableItem item) {
                final Jenkins jenkins = Jenkins.get();
                final Label label = item.getAssignedLabel();
                for (Cloud cloud : jenkins.clouds) {
                    if (cloud instanceof MockCloud && cloud.canProvision(new Cloud.CloudState(label, 0))) {
                        (label == null ? jenkins.unlabeledNodeProvisioner : label.nodeProvisioner).suggestReviewNow();
                    }
                }
            }
        }

    }

}
