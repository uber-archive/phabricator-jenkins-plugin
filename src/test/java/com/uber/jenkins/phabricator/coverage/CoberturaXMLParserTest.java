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

package com.uber.jenkins.phabricator.coverage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class CoberturaXMLParserTest {

    private static final String TEST_COVERAGE_FILE_1 = "go-torch-coverage1.xml";
    private static final String TEST_COVERAGE_FILE_2 = "go-torch-coverage2.xml";
    private static final String TEST_COVERAGE_FILE_3 = "go-torch-coverage3.xml";

    @Parameterized.Parameter
    public String workspace;

    @Test
    public void testGetLineCoverage() throws IOException, ParserConfigurationException, SAXException,
        URISyntaxException {
        CoberturaXMLParser parser = new CoberturaXMLParser(workspace);

        Path testCoverageFile = Paths.get(getClass().getResource(TEST_COVERAGE_FILE_1).toURI());
        Path testCoverageFile2 = Paths.get(getClass().getResource(TEST_COVERAGE_FILE_2).toURI());
        Path testCoverageFile3 = Paths.get(getClass().getResource(TEST_COVERAGE_FILE_3).toURI());

        Map<String, List<Integer>> lineCoverage = parser.parse(testCoverageFile.toFile(), testCoverageFile2.toFile(),
            testCoverageFile3.toFile());
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");
        assertEquals(246, mainCoverage.size());
        assertNull(mainCoverage.get(0));
        assertNull(mainCoverage.get(1));
        assertEquals(1, mainCoverage.get(78).longValue());
        assertNull(mainCoverage.get(79));
        assertEquals(1, mainCoverage.get(85).longValue());
        assertEquals(0, mainCoverage.get(102).longValue());

        List<Integer> graphCoverage = lineCoverage.get("main/github.com/uber/go-torch/graph/graph.go");
        if (workspace.isEmpty()) {
            graphCoverage = lineCoverage.get("github.com/uber/go-torch/graph/graph.go");
        }
        assertEquals(1, graphCoverage.get(234).longValue());
        assertNull(graphCoverage.get(235));
    }

    @Parameterized.Parameters
    public static Collection<String> data() {
        return Arrays.asList(new String[] {"", "/Users/aiden/src/gocode/src", "/usr/local/Cellar/go/1.5/libexec/src"});
    }
}
