// Copyright (c) 2015 Uber
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.uber.jenkins.phabricator;

import org.junit.Test;

import static org.junit.Assert.*;

public class PhabricatorPostbuildActionTest {
    private final PhabricatorPostbuildAction action = PhabricatorPostbuildAction.createShortText("text", "link");

    @Test
    public void testGetUrlName() throws Exception {
        assertEquals("", action.getUrlName());
    }

    @Test
    public void testGetDisplayName() throws Exception {
        assertEquals("", action.getDisplayName());
    }

    @Test
    public void testGetIconFileName() throws Exception {
        assertNull(action.getIconFileName());
    }

    @Test
    public void testIsTextOnly() throws Exception {
        assertTrue(action.isTextOnly());
    }

    @Test
    public void testGetIconPath() throws Exception {
        assertNull(action.getIconPath());
    }

    @Test
    public void testGetText() throws Exception {
        assertEquals("text", action.getText());
    }

    @Test
    public void testGetColor() throws Exception {
        assertNotNull(action.getColor());
    }

    @Test
    public void testGetBackground() throws Exception {
        assertNotNull(action.getBackground());
    }

    @Test
    public void testGetBorder() throws Exception {
        assertEquals("0", action.getBorder());
    }

    @Test
    public void testGetBorderColor() throws Exception {
        assertNotNull(action.getBorderColor());
    }

    @Test
    public void testGetLink() throws Exception {
        assertEquals("link", action.getLink());
    }
}
