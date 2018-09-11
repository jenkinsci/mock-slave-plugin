/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void cloud() throws Exception {
        ConfigurationAsCode.get().configure(ConfigAsCodeTest.class.getResource("cloud.yaml").toString());
        assertEquals(1, r.jenkins.clouds.size());
        MockCloud cloud = (MockCloud) r.jenkins.clouds.get(0);
        assertEquals("mock", cloud.name);
        assertEquals("", cloud.getLabels());
        assertEquals(Node.Mode.NORMAL, cloud.mode);
        assertTrue(cloud.getOneShot());
        assertEquals(1, cloud.getExecutors());
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(null); // sets canRoam
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        assertNotEquals("", b.getBuiltOnStr());
    }

}
