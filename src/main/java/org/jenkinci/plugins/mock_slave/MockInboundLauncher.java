/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public final class MockInboundLauncher extends JNLPLauncher {
    private static final Logger LOGGER = Logger.getLogger(MockInboundLauncher.class.getName());
    private transient Process proc;
    @DataBoundConstructor
    public MockInboundLauncher() {
    }
    @Override
    public boolean isLaunchSupported() {
        return proc == null;
    }
    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        LOGGER.fine(() -> "launching agent for " + computer.getName());
        try {
            File agentJar = new File(Jenkins.get().getRootDir(), "agent.jar");
            if (!agentJar.isFile()) {
                FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), agentJar);
            }
            proc = new ProcessBuilder("java", "-jar", agentJar.getAbsolutePath(), "-url", JenkinsLocationConfiguration.get().getUrl(), "-name", computer.getName(), "-secret", computer.getJnlpMac()).redirectErrorStream(true).start();
            new StreamCopyThread("I/O of " + computer.getName(), proc.getInputStream(), listener.getLogger()).start();
            Instant max = Instant.now().plus(Duration.ofSeconds(15));
            while (computer.isOffline() && Instant.now().isBefore(max)) {
                Thread.sleep(100);
            }
        } catch (Exception x) {
            Functions.printStackTrace(x, listener.error("Failed to launch"));
        }
    }
    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        LOGGER.fine(() -> "terminating agent for " + computer.getName());
        proc.destroy();
    }

    @Symbol("mockInbound")
    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @Override public String getDisplayName() {
            return "Mock Inbound Agent Launcher";
        }
    }

}
