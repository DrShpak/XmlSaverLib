package xmlSaver;

import java.util.Map;
import java.util.Set;

abstract class XmlNodeVisitor {
    abstract void beginNode(String nodeName, Set<Map.Entry<String, String>> attributes);
    abstract void beginNode(String nodeName, String nodeValue, Set<Map.Entry<String, String>> attributes);
    abstract void endNode();
}
