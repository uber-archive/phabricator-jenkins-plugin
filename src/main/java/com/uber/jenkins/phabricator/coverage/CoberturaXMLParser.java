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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import hudson.FilePath;

public class CoberturaXMLParser {

    private static final String TAG_NAME_CLASS = "class";
    private static final String TAG_NAME_SOURCE = "source";
    private static final String NODE_FILENAME = "filename";
    private static final String NODE_NAME_LINES = "lines";
    private static final String NODE_NAME_LINE = "line";
    private static final String NODE_NUMBER = "number";
    private static final String NODE_HITS = "hits";
    private final FilePath workspace;

    public CoberturaXMLParser(FilePath workspace) {
        this.workspace = workspace;
    }

    public Map<String, List<Integer>> parse(File... files) throws ParserConfigurationException, SAXException,
            IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Map<NodeList, List<String>> coverageData = new HashMap<NodeList, List<String>>();

        for (File file : files) {
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(is);
                NodeList classes = doc.getElementsByTagName(TAG_NAME_CLASS);
                List<String> sourceDirs = getSourceDirs(doc);
                coverageData.put(classes, sourceDirs);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        return parse(coverageData);
    }

    private Map<String, List<Integer>> parse(Map<NodeList, List<String>> coverageData) {
        Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<String, SortedMap<Integer, Integer>>();

        // Each entry in the map is an XML list of classes (files) mapped to its possible source roots
        for (Map.Entry<NodeList, List<String>> entry : coverageData.entrySet()) {
            NodeList classes = entry.getKey();
            List<String> sourceDirs = entry.getValue();

            String detectedSourceRoot = null;
            boolean detected = false;
            // Loop over all files in the coverage report
            for (int i = 0; i < classes.getLength(); i++) {
                Node classNode = classes.item(i);
                String fileName = classNode.getAttributes().getNamedItem(NODE_FILENAME).getTextContent();

                // On the first file in each coverage report, we need to detect the root directory
                if (!detected) {
                    detected = true;
                    // Make a guess on which of the `sourceDirs` contains the file in question
                    detectedSourceRoot = new PathResolver(workspace, sourceDirs).choose(fileName);
                }
                fileName = join(detectedSourceRoot, fileName);

                SortedMap<Integer, Integer> hitCounts = internalCounts.get(fileName);
                if (hitCounts == null) {
                    hitCounts = new TreeMap<Integer, Integer>();
                }

                NodeList children = classNode.getChildNodes();
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

    private String join(String detectedSourceRoot, String fileName) {
        if (detectedSourceRoot == null) {
            return fileName;
        }
        return String.format("%s/%s", detectedSourceRoot, fileName);
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

    private List<String> getSourceDirs(Document doc) {
        if (workspace == null) {
            return Collections.emptyList();
        }

        List<String> sourceDirs = new ArrayList<String>();
        NodeList sources = doc.getElementsByTagName(TAG_NAME_SOURCE);
        for (int i = 0; i < sources.getLength(); i++) {
            Node source = sources.item(i);
            String srcDir = source.getTextContent();
            if (srcDir.contains(workspace + "/")) {
                String relativeSrcDir = srcDir.replaceFirst(workspace + "/", "");
                if (!relativeSrcDir.isEmpty()) {
                    sourceDirs.add(relativeSrcDir);
                }
            }
        }
        return sourceDirs;
    }

    private int getIntValue(Node node, String attributeName) {
        return Integer.parseInt(node.getAttributes().getNamedItem(attributeName).getTextContent());
    }
}
