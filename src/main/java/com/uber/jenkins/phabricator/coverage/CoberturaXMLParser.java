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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class CoberturaXMLParser {
    private static final String TAG_NAME_CLASS = "class";
    private static final String NODE_FILENAME = "filename";
    private static final String NODE_NAME_LINES = "lines";
    private static final String NODE_NAME_LINE = "line";
    private static final String NODE_NUMBER = "number";
    private static final String NODE_HITS = "hits";
    private final String workspace;

    public CoberturaXMLParser(String workspace) {
        this.workspace = workspace;
    }

    public Map<String, List<Integer>> parse(InputStream xmlStream) throws IOException, SAXException, ParserConfigurationException {
        Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<String, SortedMap<Integer, Integer>>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlStream);

        NodeList classes = doc.getElementsByTagName(TAG_NAME_CLASS);
        for (int i = 0; i < classes.getLength(); i++) {
            Node classNode = classes.item(i);
            String fileName = getFileName(classNode);

            SortedMap<Integer, Integer> hitCounts = internalCounts.get(fileName);
            if (hitCounts == null) {
                hitCounts = new TreeMap<Integer, Integer>();
            }

            NodeList children = classNode.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);

                if (child.getNodeName().equals(NODE_NAME_LINES)) {
                    NodeList lines = child.getChildNodes();
                    for (int k = 0; k < lines.getLength(); k++) {
                        Node line = lines.item(k);
                        if (!line.getNodeName().equals(NODE_NAME_LINE)) {
                            continue;
                        }

                        hitCounts.put(getIntValue(line, NODE_NUMBER), getIntValue(line, NODE_HITS));
                    }
                    internalCounts.put(fileName, hitCounts);
                }
            }
        }
        return computeLineCoverage(internalCounts);
    }

    public Map<String, List<Integer>> computeLineCoverage(Map<String, SortedMap<Integer, Integer>> internalCounts) {
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

    private String getFileName(Node classNode) {
        String fileName = classNode.getAttributes().getNamedItem(NODE_FILENAME).getTextContent();
        return fileName.replaceFirst(workspace, "");
    }

    private int getIntValue(Node node, String attributeName) {
        return Integer.parseInt(node.getAttributes().getNamedItem(attributeName).getTextContent());
    }
}
