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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XmlCoverageProvider extends CoverageProvider {

    private final List<XmlCoverageHandler> xmlCoverageHandlers;
    private final Set<File> coverageReports;
    private final DocumentBuilder db;
    private final CoverageCounters cc;

    XmlCoverageProvider(Set<File> coverageReports) {
        this(coverageReports, null);
    }

    public XmlCoverageProvider(Set<File> coverageReports, Set<String> includeFiles) {
        super(includeFiles);
        this.coverageReports = coverageReports;
        this.xmlCoverageHandlers = Arrays.asList(new CoberturaXmlCoverageHandler(),
                new JacocoXmlCoverageHandler());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(true);
        DocumentBuilder localDb = null;
        try {
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            localDb = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        db = localDb;
        cc = new CoverageCounters();
    }

    @Override
    protected void computeCoverage() {
        try {
            parse(includeFiles, coverageReports);
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
        // Aggregate coverage metrics
        metrics = new CodeCoverageMetrics(
                cc.pkg.getPercent(),
                cc.file.getPercent(),
                cc.cls.getPercent(),
                cc.method.getPercent(),
                cc.line.getPercent(),
                cc.branch.getPercent(),
                cc.line.covered,
                cc.line.covered + cc.line.missed
        );
    }

    private void parse(Set<String> includeFiles, Set<File> reports) throws SAXException, IOException {
        if (db != null) {
            for (File file : reports) {
                try (InputStream is = new FileInputStream(file)) {
                    Document document = db.parse(is);
                    for (XmlCoverageHandler xmlCoverageHandler : xmlCoverageHandlers) {
                        if (xmlCoverageHandler.isApplicable(document)) {
                            xmlCoverageHandler.parseCoverage(document, includeFiles, cc, lineCoverage);
                        }
                    }
                }
            }
        }
    }

    private static Long getLongValue(NamedNodeMap attrs, String attr) {
        String content = attrs.getNamedItem(attr).getTextContent();
        try {
            return Math.round(Double.valueOf(content));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(content + " is not a valid coverage number", e);
        }
    }

    private static Integer getIntValue(NamedNodeMap attrs, String attr) {
        String content = attrs.getNamedItem(attr).getTextContent();
        try {
            return Math.round(Float.valueOf(content));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(content + " is not a valid coverage number", e);
        }
    }

    private abstract static class XmlCoverageHandler {

        abstract boolean isApplicable(Document document);

        abstract void parseCoverage(
                Document document,
                Set<String> includeFiles,
                CoverageCounters cc,
                Map<String, List<Integer>> lineCoverage);

        void computeLineCoverage(
                Map<String, SortedMap<Integer, Integer>> internalCounts,
                Map<String, List<Integer>> lineCoverage) {
            for (Map.Entry<String, SortedMap<Integer, Integer>> entry : internalCounts.entrySet()) {
                List<Integer> sortedCounts = new ArrayList<>();
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
        }
    }

    private static class CoberturaXmlCoverageHandler extends XmlCoverageHandler {

        private static final String NODE_FILENAME = "filename";
        private static final String NODE_NAME_LINES = "lines";
        private static final String NODE_NAME_LINE = "line";
        private static final String NODE_NUMBER = "number";
        private static final String NODE_HITS = "hits";

        private static final NodeList EMPTY_NODE_LIST = new NodeList() {
            @Override
            public Node item(int index) {
                return null;
            }

            @Override
            public int getLength() {
                return 0;
            }
        };

        @Override
        boolean isApplicable(Document document) {
            return document.getDocumentElement().getTagName().equals("coverage");
        }

        @Override
        void parseCoverage(
                Document document, Set<String> includeFiles,
                CoverageCounters cc,
                Map<String, List<Integer>> lineCoverage) {
            Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<>();
            NodeList classes = document.getElementsByTagName("class");

            // Collect all filenames in coverage report
            List<String> fileNames = new ArrayList<>();
            List<NodeList> childNodes = new ArrayList<>();
            for (int i = 0; i < classes.getLength(); i++) {
                Node classNode = classes.item(i);
                String fileName = classNode.getAttributes().getNamedItem(NODE_FILENAME).getTextContent();

                String finalFileName = getRelativePathFromProjectRoot(includeFiles, fileName);
                if (finalFileName != null) {
                    fileNames.add(finalFileName);
                    childNodes.add(classNode.getChildNodes());
                }
            }

            // Loop over all files which are needed for coverage report
            for (int i = 0; i < fileNames.size(); i++) {
                String fileName = fileNames.get(i);
                SortedMap<Integer, Integer> hitCounts = internalCounts.computeIfAbsent(fileName, it -> new TreeMap<>());

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

                            NamedNodeMap attrs = line.getAttributes();
                            Integer lineNumber = getIntValue(attrs, NODE_NUMBER);
                            int hits = getIntValue(attrs, NODE_HITS);
                            hitCounts.put(lineNumber, hits);

                            if (hits > 0) {
                                cc.line.covered += 1;
                            } else {
                                cc.line.missed += 1;
                            }
                        }
                    }
                }
            }
            computeLineCoverage(internalCounts, lineCoverage);

            // Update Counters
            Node root = document.getDocumentElement();
            NamedNodeMap attrs = root.getAttributes();
            // Branch coverage is only supported for cobertura coverage-04.dtd format
            if (attrs.getNamedItem("branches-covered") != null) {
                long branchesCovered = getLongValue(attrs, "branches-covered");
                long branchesValid = getLongValue(attrs, "branches-valid");
                cc.branch.covered = branchesCovered;
                cc.branch.missed = branchesValid - branchesCovered;
            }

            NodeList packages = document.getElementsByTagName("package");
            for (int i = 0; i < packages.getLength(); i++) {
                Node packageNode = packages.item(i);
                NodeList classNodes = getChildrenWithMatchingTag(packageNode, "classes");
                boolean packageCovered = false;
                for (int j = 0; j < classNodes.getLength(); j++) {
                    Node classNode = classNodes.item(j);
                    if (!classNode.getNodeName().equals("class")) {
                        continue;
                    }

                    NodeList methods = getChildrenWithMatchingTag(classNode, "methods");
                    boolean classCovered = false;
                    for (int k = 0; k < methods.getLength(); k++) {
                        Node methodNode = methods.item(k);
                        if (!methodNode.getNodeName().equals("method")) {
                            continue;
                        }
                        NodeList lines = getChildrenWithMatchingTag(methodNode, "lines");
                        boolean methodCovered = false;
                        for (int l = 0; l < lines.getLength(); l++) {
                            Node lineNode = lines.item(l);
                            if (!lineNode.getNodeName().equals("line")) {
                                continue;
                            }
                            int hits = getIntValue(lineNode.getAttributes(), "hits");
                            if (hits > 0) {
                                methodCovered = true;
                                break;
                            }
                        }
                        if (methodCovered) {
                            cc.method.covered += 1;
                            classCovered = true;
                        } else {
                            cc.method.missed += 1;
                        }
                    }
                    if (classCovered) {
                        cc.cls.covered += 1;
                        packageCovered = true;
                    } else {
                        cc.cls.missed += 1;
                    }
                }
                if (packageCovered) {
                    cc.pkg.covered += 1;
                } else {
                    cc.pkg.missed += 1;
                }
            }
        }

        private static NodeList getChildrenWithMatchingTag(Node node, String tag) {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeName().equals(tag)) {
                    return children.item(i).getChildNodes();
                }
            }
            return EMPTY_NODE_LIST;
        }
    }

    private static class JacocoXmlCoverageHandler extends XmlCoverageHandler {

        @Override
        boolean isApplicable(Document document) {
            return document.getDocumentElement().getTagName().equals("report");
        }

        @Override
        void parseCoverage(
                Document document, Set<String> includeFiles,
                CoverageCounters cc,
                Map<String, List<Integer>> lineCoverage) {
            Map<String, SortedMap<Integer, Integer>> internalCounts = new HashMap<>();
            NodeList packages = document.getElementsByTagName("package");

            // Compute line coverage
            for (int i = 0; i < packages.getLength(); i++) {
                Node packageNode = packages.item(i);
                String packageName = packageNode.getAttributes().getNamedItem("name").getTextContent();
                NodeList children = packageNode.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node childNode = children.item(j);
                    if (childNode.getNodeName().equals("sourcefile")) {
                        String fileName = packageName
                                + File.separatorChar
                                + childNode.getAttributes().getNamedItem("name").getTextContent();
                        String finalFileName = getRelativePathFromProjectRoot(includeFiles, fileName);
                        if (finalFileName != null) {
                            SortedMap<Integer, Integer> hitCounts = internalCounts.computeIfAbsent(
                                    finalFileName, it -> new TreeMap<>());
                            NodeList coverage = childNode.getChildNodes();
                            for (int k = 0; k < coverage.getLength(); k++) {
                                Node coverageNode = coverage.item(k);
                                if (coverageNode != null && "line".equals(coverageNode.getNodeName())) {
                                    NamedNodeMap attrs = coverageNode.getAttributes();
                                    long hitCount = getIntValue(attrs, "ci");
                                    int lineNumber = getIntValue(attrs, "nr");
                                    hitCounts.put(lineNumber, hitCount > 0 ? 1 : 0);
                                }
                            }
                        }
                    }
                }
            }
            computeLineCoverage(internalCounts, lineCoverage);

            // Update Counters
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!node.getNodeName().equals("counter")) {
                    continue;
                }
                NamedNodeMap attrs = node.getAttributes();
                long covered = Long.valueOf(attrs.getNamedItem("covered").getTextContent());
                long missed = Long.valueOf(attrs.getNamedItem("missed").getTextContent());
                switch (attrs.getNamedItem("type").getTextContent()) {
                    case "CLASS":
                        cc.cls.covered += covered;
                        cc.cls.missed += missed;
                        break;
                    case "METHOD":
                        cc.method.covered += covered;
                        cc.method.missed += missed;
                        break;
                    case "LINE":
                        cc.line.covered += covered;
                        cc.line.missed += missed;
                        break;
                    case "BRANCH":
                        cc.branch.covered += covered;
                        cc.branch.missed += missed;
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static class CoverageCounter {

        long covered = 0;
        long missed = 0;

        float getPercent() {
            long total = covered + missed;
            if (total == 0) {
                return 100.0f;
            } else {
                return (covered * 1.0f / total * 1.0f) * 100.0f;
            }
        }
    }

    private static class CoverageCounters {

        private final CoverageCounter pkg = new CoverageCounter();
        private final CoverageCounter cls = new CoverageCounter();
        private final CoverageCounter method = new CoverageCounter();
        private final CoverageCounter line = new CoverageCounter();
        private final CoverageCounter branch = new CoverageCounter();
        private final CoverageCounter file = new CoverageCounter();
    }
}
