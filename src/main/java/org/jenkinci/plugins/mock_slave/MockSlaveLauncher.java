/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class MockSlaveLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(MockSlaveLauncher.class.getName());

    public final int latency;
    public final int bandwidth;
    
    @DataBoundConstructor
    public MockSlaveLauncher(int latency, int bandwidth) {
        this.latency = latency;
        this.bandwidth = bandwidth;
    }

    @Override public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        Listener.launchTimes.put(computer, System.currentTimeMillis());
        listener.getLogger().println("Launching");
        File slaveJar = Which.jarFile(Which.class);
        if (!slaveJar.isFile()) {
            slaveJar = File.createTempFile("slave", ".jar");
            slaveJar.deleteOnExit();
            FileUtils.copyURLToFile(new Slave.JnlpJar("slave.jar").getURL(), slaveJar);
        }
        final EnvVars cookie = EnvVars.createCookie();
        InputStream is;
        OutputStream os;
        final Process proc;
        if (latency > 0 || bandwidth > 0) {
            File portFile = File.createTempFile("jenkins-port", "");
            final ProcessBuilder pb = new ProcessBuilder("java", "-jar", slaveJar.getAbsolutePath(), "-tcp", portFile.getAbsolutePath());
            pb.environment().putAll(cookie);
            proc = pb.start();
            while (portFile.length() == 0) {
                Thread.sleep(100);
            }
            int port = Integer.parseInt(FileUtils.readFileToString(portFile));
            listener.getLogger().println("connecting to localhost:" + port);
            Socket s = new Socket(getLoopbackAddress(), port);
            is = s.getInputStream();
            os = s.getOutputStream();
            listener.getLogger().printf("throttling with latency=%dms bandwidth=%dbps%n", latency, bandwidth);
            Throttler t = new Throttler(latency, bandwidth, is, os);
            is = t.is();
            os = t.os();
        } else {
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", slaveJar.getAbsolutePath());
            pb.environment().putAll(cookie);
            proc = pb.start();
            is = proc.getInputStream();
            os = proc.getOutputStream();
        }
        new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(), proc.getErrorStream(), listener.getLogger()).start();
        computer.setChannel(is, os, listener.getLogger(), new Channel.Listener() {
            @Override public void onClosed(Channel channel, IOException cause) {
                Jenkins j = Jenkins.getInstance();
                if (j == null || j.isTerminating()) {
                    LOGGER.log(Level.INFO, "Leaving processes running on {0} during shutdown", computer.getName());
                } else {
                    LOGGER.log(Level.FINE, "Killing any processes still running on {0}", computer.getName());
                    try {
                        ProcessTree.get().killAll(proc, cookie);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.INFO, "interrupted", e);
                    }
                }
            }
        });
        LOGGER.log(Level.INFO, "agent launched for {0}", computer.getDisplayName());
    }

    @IgnoreJRERequirement
    private InetAddress getLoopbackAddress() throws UnknownHostException {
        try {
            return InetAddress.getLoopbackAddress();
        } catch (NoSuchMethodError x) { // JDK 5/6
            return InetAddress.getLocalHost();
        }
    }

    @Symbol("mock")
    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public String getDisplayName() {
            return "Mock Agent Launcher";
        }
    }

    @Extension
    public static class Listener extends ComputerListener {
        static final Map<Computer,Long> launchTimes = new WeakHashMap<Computer,Long>();
        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            Long launchTime = launchTimes.remove(c);
            if (launchTime != null) {
                long seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - launchTime);
                listener.getLogger().printf("Launched in %ds%n", seconds);
                LOGGER.log(Level.INFO, "Launched {0} in {1}s", new Object[] {c.getName(), seconds});
            }
        }
    }

}
