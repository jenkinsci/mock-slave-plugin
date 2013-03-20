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
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

public class MockSlaveLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(MockSlaveLauncher.class.getName());

    @DataBoundConstructor
    public MockSlaveLauncher() {
    }
    
    @Override public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Launching");
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", Which.jarFile(Which.class).getAbsolutePath());
        final EnvVars cookie = EnvVars.createCookie();
        pb.environment().putAll(cookie);
        final Process proc = pb.start();
        new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(), proc.getErrorStream(), listener.getLogger()).start();
        computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
            @Override public void onClosed(Channel channel, IOException cause) {
                try {
                    ProcessTree.get().killAll(proc, cookie);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.INFO, "interrupted", e);
                }
            }
        });
        LOGGER.log(Level.INFO, "slave agent launched for {0}", computer.getDisplayName());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public String getDisplayName() {
            return "Mock Slave Launcher";
        }
    }

}
