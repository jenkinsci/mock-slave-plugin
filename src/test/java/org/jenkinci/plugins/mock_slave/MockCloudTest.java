/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Label;
import java.io.IOException;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.awaitility.Awaitility;
import static org.hamcrest.Matchers.empty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.TailLog;
import org.jvnet.hudson.test.TestBuilder;

public class MockCloudTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(MockCloud.class, Level.FINE);

    @Test public void outbound() throws Exception {
        smokeTest(new MockCloud("mock"));
    }

    @Test public void inbound() throws Exception {
        var cloud = new MockCloud("mock");
        cloud.setInbound(true);
        smokeTest(cloud);
    }

    private void smokeTest(MockCloud cloud) throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());
        cloud.setLabels("mock");
        r.jenkins.clouds.add(cloud);
        var p = r.createFreeStyleProject("p");
        p.setAssignedLabel(Label.get("mock"));
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                try {
                    var c = build.getBuiltOn().toComputer();
                    var logText = c.getLogText();
                    var out = PrefixedOutputStream.builder().withColor(PrefixedOutputStream.Color.YELLOW).withName(c.getName()).build(System.out);
                    Computer.threadPoolForRemoting.submit(() -> {
                        long pos = 0;
                        while (!logText.isComplete()) {
                            pos = logText.writeLogTo(pos, out);
                            Thread.sleep(100);
                        }
                        return null;
                    });
                    Thread.sleep(3_000);
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
                return true;
            }
        });
        try (var tail = new TailLog(r, "p", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            r.buildAndAssertSuccess(p);
            tail.waitForCompletion();
        }
        Awaitility.await().until(() -> r.jenkins.getNodes(), empty());
        if (Functions.isWindows()) {
            // Need to wait for Tailer to close the log file.
            Thread.sleep(5_000);
        }
    }

}
