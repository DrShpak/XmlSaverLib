package xmlSaver;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

class XmlNodeWriter extends XmlNodeVisitor {
    private final File file;
    private final String indentationString;

    private PrintWriter pw;
    private int depth;
    private final ArrayDeque<String> conclusions = new ArrayDeque<>();

    XmlNodeWriter(String savePath) {
        this.file = new File(savePath);
        this.indentationString = "\t";
        this.depth = 0;
    }

    void save(XmlNode node) {
        if (this.file.exists()) {
            if (!this.file.delete()) {
                throw new IllegalArgumentException("old file " + this.file.getPath() + " cannot be deleted!");
            }
        }
        try {
            if (!this.file.createNewFile()) {
                throw new IllegalArgumentException("file " + this.file.getPath() + " cannot be created!");
            }
            pw = new PrintWriter(this.file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        node.visit(this);
        this.pw.close();
    }

    @Override
    void beginNode(String nodeName, Set<Map.Entry<String, String>> attributes) {
        var sub = String.format(
                "%s<%s%s>",
                this.indentationString.repeat(this.depth),
                nodeName,
                computeAttributesString(attributes)
        );
        var conclusion = String.format(
                "%s</%s>",
                this.indentationString.repeat(this.depth),
                nodeName
        );
        this.conclusions.push(conclusion);
        this.pw.println(sub);
        this.depth++;
    }

    @Override
    void beginNode(String nodeName, String nodeValue, Set<Map.Entry<String, String>> attributes) {
        String sub;
        if (nodeValue == null || nodeValue.isEmpty()) {
            sub = String.format(
                    "%s<%s%s/>",
                    this.indentationString.repeat(this.depth),
                    nodeName,
                    computeAttributesString(attributes)
            );
        } else {
            sub = String.format(
                    "%s<%s%s>%s</%s>",
                    this.indentationString.repeat(this.depth),
                    nodeName,
                    computeAttributesString(attributes),
                    nodeValue,
                    nodeName
            );
        }
        this.conclusions.push("");
        this.pw.println(sub);
        this.depth++;
    }

    @Override
    void endNode() {
        var conclusion = this.conclusions.isEmpty()? null : this.conclusions.pop();
        if (conclusion != null && !conclusion.isEmpty()) {
            this.pw.println(conclusion);
        }
        this.depth = this.depth > 0 ? this.depth - 1 : 0;
    }

    private static String computeAttributesString(Set<Map.Entry<String, String>> attributes) {
        if (attributes.isEmpty()) {
            return "";
        }
        return attributes.stream().reduce(
            "",
             (x, y) -> x + String.format(" %s=\"%s\"", y.getKey(), y.getValue()),
             (x, y) -> x + y
        );
    }
}
