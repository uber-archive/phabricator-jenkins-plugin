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

import com.google.common.collect.ImmutableSet;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public final class CoverageXMLParser {

    private static final String NODE_FILENAME = "filename";
    private static final String NODE_NAME_LINES = "lines";
    private static final String NODE_NAME_LINE = "line";
    private static final String NODE_NUMBER = "number";
    private static final String NODE_HITS = "hits";
    private static final String EMPTY_XML = "<?xml version='1.0' encoding='UTF-8'?>";
    private static final Logger LOGGER = Logger.getLogger(CoverageXMLParser.class.getName());

    private CoverageXMLParser() {}

    static Map<String, List<Integer>> parseCobertura(Set<String> includeFiles, File... reports) throws
            ParserConfigurationException, SAXException,
            IOException {
        Map<String, List<Integer>> hits = new HashMap<String, List<Integer>>();
        for (CoverageHandler handler : ImmutableSet.of(COBERTURA_HANDLER, JACOCO_HANDLER)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db;
            Set<NodeList> coverageData = new HashSet<NodeList>();
            for (File file : reports) {
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    db = dbf.newDocumentBuilder();
                    db.setEntityResolver(handler);
                    coverageData.add(db.parse(is).getElementsByTagName(handler.getCoverageTag()));
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            }
            handler.parseCoverage(coverageData, includeFiles, hits);
        }

        return hits;
    }

    private static final CoverageHandler COBERTURA_HANDLER = new CoverageHandler() {
        private final Set<String> coberturaDtD = ImmutableSet.<String>builder()
                .add("http://cobertura.sourceforge.net/xml/coverage-01.dtd")
                .add("http://cobertura.sourceforge.net/xml/coverage-02.dtd")
                .add("http://cobertura.sourceforge.net/xml/coverage-03.dtd")
                .add("http://cobertura.sourceforge.net/xml/coverage-04.dtd")
                .build();

        @Override
        String resource(String systemId) {
            if (coberturaDtD.contains(systemId)) {
                String[] parts = systemId.split("/");
                return parts[parts.length - 1];
            } else {
                return null;
            }
        }

        @Override
        String getCoverageTag() {
            return "class";
        }

        @Override
        void parseCoverage(
                Set<NodeList> coverageData, Set<String> includeFiles,
                Map<String, List<Integer>> hits) {
            Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<String, SortedMap<Integer, Integer>>();
            // Each entry in the set is an XML list of classes (files)
            for (NodeList classes : coverageData) {
                // Collect all filenames in coverage report
                List<String> fileNames = new ArrayList<String>();
                List<NodeList> childNodes = new ArrayList<NodeList>();
                for (int i = 0; i < classes.getLength(); i++) {
                    Node classNode = classes.item(i);
                    String fileName = classNode.getAttributes().getNamedItem(NODE_FILENAME).getTextContent();

                    String finalFileName = CoverageProvider.getRelativePathFromProjectRoot(includeFiles, fileName);
                    if (finalFileName != null) {
                        fileNames.add(finalFileName);
                        childNodes.add(classNode.getChildNodes());
                    }
                }

                // Loop over all files which are needed for coverage report
                for (int i = 0; i < fileNames.size(); i++) {
                    String fileName = fileNames.get(i);

                    SortedMap<Integer, Integer> hitCounts = internalCounts.get(fileName);
                    if (hitCounts == null) {
                        hitCounts = new TreeMap<Integer, Integer>();
                    }

                    NodeList children = childNodes.get(i);
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);

                        if (NODE_NAME_LINES.equals(child.getNodeName())) {
                            NodeList lines = child.getChildNodes();
                            for (int k = 0; k < lines.getLength(); k++) {
                                Node line = lines.item(k);
                                if (!NODE_NAME_LINE.equals(line.getNodeName())) {
                                    continue;
                                }

                                Integer lineNumber = getIntValue(line, NODE_NUMBER);
                                int existingHits = hitCounts.containsKey(lineNumber) ? hitCounts.get(lineNumber) : 0;
                                hitCounts.put(lineNumber, Math.max(existingHits, getIntValue(line, NODE_HITS)));
                            }
                            internalCounts.put(fileName, hitCounts);
                        }
                    }
                }

            }
            computeLineCoverage(internalCounts, hits);
        }
    };

    private static final CoverageHandler JACOCO_HANDLER = new CoverageHandler() {
        private static final String JACOCO_DTD = "https://www.jacoco.org/jacoco/trunk/coverage/report.dtd";

        @Override
        String resource(String systemId) {
            if (JACOCO_DTD.equals(systemId)) {
                return "report.dtd";
            } else {
                return null;
            }
        }

        @Override
        String getCoverageTag() {
            return "package";
        }

        @Override
        void parseCoverage(
                Set<NodeList> coverageData, Set<String> includeFiles, Map<String, List<Integer>> hits) {

        }
    };

    private static abstract class CoverageHandler implements EntityResolver {

        abstract String resource(String systemId);

        abstract String getCoverageTag();

        abstract void parseCoverage(
                Set<NodeList> coverageData, Set<String> includeFiles,
                Map<String, List<Integer>> hits);

        @Override
        public final InputSource resolveEntity(String publicId, String systemId) {
            String res = resource(systemId);
            if (res != null) {
                return new InputSource(this.getClass().getResourceAsStream(res));
            } else {
                LOGGER.log(Level.WARNING,
                        "Unknown DTD systemID \"" + systemId + "\", skipping download by returning empty DTD");
                return new InputSource(new StringReader(EMPTY_XML));
            }
        }

        static void computeLineCoverage(
                Map<String, SortedMap<Integer, Integer>> internalCounts, Map<String,
                List<Integer>> hits) {
            for (Map.Entry<String, SortedMap<Integer, Integer>> entry : internalCounts.entrySet()) {
                List<Integer> sortedCounts = new ArrayList<Integer>();
                int startIndex = 1;
                for (Map.Entry<Integer, Integer> line : entry.getValue().entrySet()) {
                    for (int i = startIndex; i < line.getKey(); i++) {
                        sortedCounts.add(null);
                        startIndex++;
                    }
                    sortedCounts.add(line.getValue());
                    startIndex++;
                }
                hits.put(entry.getKey(), sortedCounts);
            }
        }

        static int getIntValue(Node node, String attributeName) {
            return Integer.parseInt(node.getAttributes().getNamedItem(attributeName).getTextContent());
        }
    }
}
