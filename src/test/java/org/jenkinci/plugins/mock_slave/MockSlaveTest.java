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

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.htmlunit.html.HtmlTextInput;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class MockSlaveTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void basics() throws Exception {
        Node slave = new MockSlave("test-slave");
        r.jenkins.addNode(slave);
        FreeStyleProject j = r.createFreeStyleProject();
        j.setAssignedNode(slave);
        FreeStyleBuild b = r.assertBuildStatusSuccess(j.scheduleBuild2(0));
        assertEquals(slave, b.getBuiltOn());
    }

    @Issue("JENKINS-66742")
    @Test public void guiCreation() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage computerNewPage = wc.goTo("computer/new");
        HtmlForm createItemForm = computerNewPage.getFormByName("createItem");
        HtmlTextInput nameText = createItemForm.getInputByName("name");
        nameText.setText("xxx");
        HtmlRadioButtonInput modeRadio = createItemForm.getInputByValue("org.jenkinci.plugins.mock_slave.MockSlave");
        modeRadio.setChecked(true);
        HtmlPage createItemPage = r.submit(createItemForm);
        HtmlForm configForm = createItemPage.getFormByName("config");
        r.submit(configForm);
        assertThat(r.jenkins.getNode("xxx"), isA(MockSlave.class));
    }

}
