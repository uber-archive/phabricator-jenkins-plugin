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

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
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
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.collect.ImmutableMap;

import hudson.FilePath;

public class CoberturaXMLParser {

    private static final String TAG_NAME_CLASS = "class";
    private static final String NODE_FILENAME = "filename";
    private static final String NODE_NAME_LINES = "lines";
    private static final String NODE_NAME_LINE = "line";
    private static final String NODE_NUMBER = "number";
    private static final String NODE_HITS = "hits";
    private static final String EMPTY_XML = "<?xml version='1.0' encoding='UTF-8'?>";
    private static final Logger LOGGER = Logger.getLogger(CoberturaXMLParser.class.getName());
    private static final Map<String, String> dtdMap = ImmutableMap.<String, String>builder()
        .put("http://cobertura.sourceforge.net/xml/coverage-01.dtd", "coverage-01.dtd")
        .put("http://cobertura.sourceforge.net/xml/coverage-02.dtd", "coverage-02.dtd")
        .put("http://cobertura.sourceforge.net/xml/coverage-03.dtd", "coverage-03.dtd")
        .put("http://cobertura.sourceforge.net/xml/coverage-04.dtd", "coverage-04.dtd")
        .build();

    private static final EntityResolver entityResolver = new EntityResolver() {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            String res = dtdMap.get(systemId);
            if (res != null) {
                return new InputSource(this.getClass().getResourceAsStream(res));
            } else {
                LOGGER.log(Level.WARNING, "Unknown DTD systemID \"" + systemId + "\", skipping download by returning empty DTD");
                return new InputSource(new StringReader(EMPTY_XML));
            }
        }
    };

    private final Set<String> includeFiles;
    private final Set<String> includeFileNames = new HashSet<String>();

    CoberturaXMLParser(Set<String> includeFiles) {
        this.includeFiles = includeFiles != null ? includeFiles : Collections.<String>emptySet();
        for(String includeFile: this.includeFiles) {
            includeFileNames.add(FilenameUtils.getName(includeFile));
        }
    }

    Map<String, List<Integer>> parse(File... files) throws ParserConfigurationException, SAXException,
            IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Set<NodeList> coverageData = new HashSet<NodeList>();

        for (File file : files) {
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                db = dbf.newDocumentBuilder();
                db.setEntityResolver(entityResolver);
                coverageData.add(db.parse(is).getElementsByTagName(TAG_NAME_CLASS));
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        return parse(coverageData);
    }

    private Map<String, List<Integer>> parse(Set<NodeList> coverageData) {
        Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<String, SortedMap<Integer, Integer>>();

        // Each entry in the map is an XML list of classes (files) mapped to its possible source roots
        for (NodeList classes : coverageData) {
            // Collect all filenames in coverage report
            List<String> fileNames = new ArrayList<String>();
            List<NodeList> childNodes = new ArrayList<NodeList>();
            for (int i = 0; i < classes.getLength(); i++) {
                Node classNode = classes.item(i);
                String fileName = classNode.getAttributes().getNamedItem(NODE_FILENAME).getTextContent();

                if (includeFiles.isEmpty() || includeFiles.contains(fileName) || includeFileNames.contains(fileName)) {
                    fileNames.add(fileName);
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
        return computeLineCoverage(internalCounts);
    }

    private Map<String, List<Integer>> computeLineCoverage(Map<String, SortedMap<Integer, Integer>> internalCounts) {
        Map<String, List<Integer>> lineCoverage = new HashMap<String, List<Integer>>();
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
            lineCoverage.put(entry.getKey(), sortedCounts);
        }
        return lineCoverage;
    }

    private int getIntValue(Node node, String attributeName) {
        return Integer.parseInt(node.getAttributes().getNamedItem(attributeName).getTextContent());
    }
}
