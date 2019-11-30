package xmlSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class XmlNodeReader {
    private final File file;
    private List<String> lines;
    private final ArrayDeque<XmlNode> nodes = new ArrayDeque<>();

    XmlNodeReader(String savePath) {
        this.file = new File(savePath);
    }

    XmlNode load() {
        if (!this.file.exists()) {
            throw new IllegalArgumentException("file " + this.file.getPath() + " does not exist!");
        }
        try {
            this.lines = Files.
                    readAllLines(Paths.get(this.file.getPath())).stream().
                    map(x -> x.replaceFirst("^\\s+", "")).
                    collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        processLines();
        return this.nodes.pop();
    }

    private void processLines() {
        for (String line : this.lines) {
            var startTagMatcher = Pattern.compile("^<?([^<>/]+?)( [^<>/]+?)?>$").matcher(line);
            if (startTagMatcher.matches()) {
                var newNode = new XmlNode(startTagMatcher.group(1));
                parseAttributes(startTagMatcher.group(2)).
                        forEach(x -> newNode.appendAttribute(x.getKey(), x.getValue()));
                if (!this.nodes.isEmpty()) {
                    this.nodes.peek().appendChild(newNode);
                }
                this.nodes.push(newNode);
                continue;
            }
            var endTagMatcher = Pattern.compile("^</.+?>$").matcher(line);
            if (endTagMatcher.matches()) {
                if (this.nodes.size() > 1) {
                    this.nodes.pop();
                }
                continue;
            }
            var emptyTag = Pattern.compile("^<(.+?)( [^<>/]+?)?/>$").matcher(line);
            if (emptyTag.matches()) {
                var newNode = new XmlNode(emptyTag.group(1));
                parseAttributes(emptyTag.group(2)).
                        forEach(x -> newNode.appendAttribute(x.getKey(), x.getValue()));
                assert this.nodes.peek() != null;
                this.nodes.peek().appendChild(newNode);
                continue;
            }
            var defaultTag = Pattern.compile("^<(.+?)( [^<>/]+?)?>(.+?)</\\1>$").matcher(line);
            if (defaultTag.matches())
            {
                var newNode = new XmlNode(defaultTag.group(1), defaultTag.group(3));
                parseAttributes(defaultTag.group(2)).
                        forEach(x -> newNode.appendAttribute(x.getKey(), x.getValue()));
                assert this.nodes.peek() != null;
                this.nodes.peek().appendChild(newNode);
                continue;
            }
            throw new IllegalStateException(
                    String.format("unknown token at line <%d> - `%s`", lines.indexOf(line), line)
            );
        }
    }

    private List<Map.Entry<String, String>> parseAttributes(String attrString) {
        //noinspection OptionalGetWithoutIsPresent
        return Pattern.compile(" (.+?=\".+?\")").
                matcher(attrString != null ? attrString : "").
                results().
                map(x ->
                    Pattern.compile("(.+?)=\"(.+?)\"").
                    matcher(x.group(1)).
                    results().
                    findFirst().
                    get()
                ).
                map(x -> Map.entry(x.group(1), x.group(2))).
                collect(Collectors.toList());
    }
}
