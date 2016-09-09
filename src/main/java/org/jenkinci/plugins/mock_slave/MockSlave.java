/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.File;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public final class MockSlave extends Slave {

    @DataBoundConstructor public MockSlave(String name, int numExecutors, Mode mode, String labelString, RetentionStrategy<?> retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException, Descriptor.FormException {
    	super(name, "", root(name), numExecutors, mode, labelString, new MockSlaveLauncher(0, 0), retentionStrategy, nodeProperties);
    }

    /** Provides a predictable {@code remoteFS} unique for a given slave name and Jenkins instance. */
    static String root(String slaveName) {
        return new File(new File(Jenkins.getInstance().getRootDir(), "mock-agents"), slaveName).getAbsolutePath();
    }
    
    @Extension public static final class DescriptorImpl extends SlaveDescriptor {

        @Override public String getDisplayName() {
            return "Mock Agent";
        }

    }

}
