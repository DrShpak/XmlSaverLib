package xmlSaver;

import xmlSaver.XmlNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;

class XmlNode {
    private final String nodeName;
    private String nodeValue;
    private final HashMap<String, String> attributes = new HashMap<>();
    private final ArrayList<XmlNode> childNodes = new ArrayList<>();

    XmlNode(String nodeName) {
        this.nodeName = nodeName;
    }

    XmlNode(String nodeName, XmlNode parentChild) {
        this.nodeName = nodeName;
        parentChild.appendChild(this);
    }

    XmlNode(String nodeName, String nodeValue) {
        this.nodeName = nodeName;
        this.nodeValue = nodeValue;
    }

    XmlNode getChildNode(String nodeName) {
        return childNodes.stream().
            filter(x -> x.nodeName.equals(nodeName)).
            findFirst().
            orElseThrow();
    }

    XmlNode[] getChildNodes(@SuppressWarnings("SameParameterValue") String nodeName) {
        return childNodes.stream().
            filter(x -> x.nodeName.equals(nodeName)).
            toArray(XmlNode[]::new);
    }

    String getAttribute(@SuppressWarnings("SameParameterValue") String attrName) {
        return this.attributes.get(attrName);
    }

    boolean hasAttribute(@SuppressWarnings("SameParameterValue") String attrName) {
        return this.attributes.containsKey(attrName);
    }

    void appendAttribute(@SuppressWarnings("SameParameterValue") String attrName, String attrValue) {
        this.attributes.put(attrName, attrValue);
    }

    void setValue(String content) {
        this.nodeValue = content;
    }

    void appendChild(XmlNode child) {
        this.childNodes.add(child);
    }

    void visit(XmlNodeVisitor visitor) {
        if (this.childNodes.isEmpty()) {
            visitor.beginNode(this.nodeName, this.nodeValue, this.attributes.entrySet());
        } else {
            visitor.beginNode(this.nodeName, this.attributes.entrySet());
        }
        for (XmlNode childNode : childNodes) {
            childNode.visit(visitor);
        }
        visitor.endNode();
    }

    String getNodeValue() {
        return nodeValue;
    }
}
