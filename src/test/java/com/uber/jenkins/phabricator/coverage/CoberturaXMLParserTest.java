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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CoberturaXMLParserTest {

    private static final String TEST_COVERAGE_FILE = "go-torch-coverage.xml";
    private static final String TEST_COVERAGE_FILE_1 = "go-torch-coverage1.xml";
    private static final String TEST_COVERAGE_FILE_2 = "go-torch-coverage2.xml";
    private static final String TEST_COVERAGE_FILE_3 = "go-torch-coverage3.xml";
    private static final String TEST_COVERAGE_FILE_OVERWRITE = "go-torch-coverage_overwrite.xml";
    private static final String TEST_COVERAGE_FILE_MULTIPLE_INCLUDE = "multiple-include-coverage.xml";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testGetLineCoverage() throws IOException, ParserConfigurationException, SAXException,
            URISyntaxException {
        File testCoverageFile = getResource(TEST_COVERAGE_FILE_1);
        File testCoverageFile2 = getResource(TEST_COVERAGE_FILE_2);
        File testCoverageFile3 = getResource(TEST_COVERAGE_FILE_3);

        Map<String, List<Integer>> lineCoverage = CoberturaXMLParser.parse(null, testCoverageFile,
                testCoverageFile2, testCoverageFile3);
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");
        assertEquals(246, mainCoverage.size());
        assertNull(mainCoverage.get(0));
        assertNull(mainCoverage.get(1));
        assertEquals(1, mainCoverage.get(78).longValue());
        assertNull(mainCoverage.get(79));
        assertEquals(1, mainCoverage.get(85).longValue());
        assertEquals(0, mainCoverage.get(102).longValue());

        List<Integer> graphCoverage = lineCoverage.get("github.com/uber/go-torch/graph/graph.go");
        assertEquals(1, graphCoverage.get(234).longValue());
        assertNull(graphCoverage.get(235));
    }

    @Test
    public void testGetLineCoverageWhenOneFileOverwriteTheOther()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        // In `TEST_COVERAGE_FILE`, line 212 has 1 hit
        File testCoverageFile = getResource(TEST_COVERAGE_FILE_1);
        // In `TEST_COVERAGE_FILE_OVERWRITE`, line 212 has 0 hit
        File testCoverageFileOverwrite = getResource(TEST_COVERAGE_FILE_OVERWRITE);

        Map<String, List<Integer>> lineCoverage = CoberturaXMLParser.parse(null, testCoverageFile,
                testCoverageFileOverwrite);
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");

        // Line 212 is recorded as hit
        assertEquals(1, mainCoverage.get(212).longValue());
    }

    @Test
    public void testGetLineCoverageWithIncludes()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        File testCoverageFile = getResource(TEST_COVERAGE_FILE);

        Map<String, List<Integer>> lineCoverage = CoberturaXMLParser.parse(
                Collections.singleton("github.com/uber/go-torch/main.go"), testCoverageFile);
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");
        assertEquals(1, mainCoverage.get(212).longValue());

        List<Integer> graphCoverage = lineCoverage.get("github.com/uber/go-torch/graph.go");
        assertNull(graphCoverage);

    }

    @Test
    public void testGetLineCoverageWithMultipleIncludes()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        File testCoverageFile = getResource(TEST_COVERAGE_FILE_MULTIPLE_INCLUDE);

        Map<String, List<Integer>> lineCoverage = CoberturaXMLParser.parse(
                new HashSet<String>(Arrays.asList("com/uber/jenkins/phabricator/packageA/Greet.java", "com/uber/jenkins"
                        + "/phabricator/packageB/Greet.java", "eet.java", "kageB/Greet.java")), testCoverageFile);
        List<Integer> greetACoverage = lineCoverage.get("com/uber/jenkins/phabricator/packageA/Greet.java");
        List<Integer> greetBCoverage = lineCoverage.get("com/uber/jenkins/phabricator/packageB/Greet.java");
        List<Integer> eetCoverage = lineCoverage.get("eet.java");
        List<Integer> partialMatchCoverage = lineCoverage.get("kageB/Greet.java");
        assertEquals(0, greetACoverage.get(6).longValue());
        assertEquals(1, greetBCoverage.get(6).longValue());
        assertNull(eetCoverage);
        assertNull(partialMatchCoverage);
    }

    private File getResource(String fileName) throws URISyntaxException {
        return Paths.get(getClass().getResource(fileName).toURI()).toFile();
    }
}
