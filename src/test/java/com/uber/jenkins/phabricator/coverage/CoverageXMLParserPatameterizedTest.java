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

import com.google.common.io.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class CoverageXMLParserPatameterizedTest {

    private static final String TEST_COVERAGE_PYTHON = "python-coverage.xml";

    public Boolean createDirectory;

    public CoverageXMLParserPatameterizedTest(Boolean createDirectory) {
        this.createDirectory = createDirectory;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false},
                {true},
        });
    }

    @Test
    public void testDetectCoverageRoot() throws Exception {
        File tmpDir = Files.createTempDir();
        File example = new File(tmpDir, "example");

        try {
            if (createDirectory) {
                example.mkdir();
            }
            Map<String, List<Integer>> lineCoverage = CoverageXMLParser.parse(null, getResource(TEST_COVERAGE_PYTHON));
            List<Integer> libCoverage = lineCoverage.get("example/lib.py");
            assertEquals(1, libCoverage.get(2).longValue());
            assertNull(libCoverage.get(1));
        } finally {
            if (createDirectory) {
                example.delete();
            }
            tmpDir.delete();
        }
    }

    private File getResource(String fileName) throws URISyntaxException {
        return Paths.get(getClass().getResource(fileName).toURI()).toFile();
    }
}
