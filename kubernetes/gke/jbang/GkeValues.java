import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Typed, dotted-path view over the jbang values YAML — mirrors
 * {@code dvara-infra/jbang/Values.java}. A required value left as {@code REPLACE_ME}
 * makes {@link #text(String)} throw, so a half-filled file fails fast instead of
 * provisioning garbage.
 */
public final class GkeValues {

    private static final ObjectMapper YAML = new YAMLMapper();

    private final JsonNode root;

    private GkeValues(JsonNode root) { this.root = root; }

    public static GkeValues load(Path file) throws IOException {
        JsonNode root = YAML.readTree(file.toFile());
        if (root == null || root.isMissingNode()) throw new IOException("empty values file: " + file);
        return new GkeValues(root);
    }

    /** For tests. */
    public static GkeValues of(String yaml) throws IOException {
        return new GkeValues(YAML.readTree(yaml));
    }

    /** Required: throws if missing or still REPLACE_ME. */
    public String text(String dotted) {
        JsonNode n = at(dotted);
        if (n == null || n.isMissingNode() || n.isNull())
            throw new IllegalStateException("missing required value: " + dotted);
        String v = n.asText();
        if (v.contains("REPLACE_ME"))
            throw new IllegalStateException("value not filled in (REPLACE_ME): " + dotted);
        return v;
    }

    public String text(String dotted, String defaultValue) {
        JsonNode n = at(dotted);
        return (n == null || n.isMissingNode() || n.isNull()) ? defaultValue : n.asText();
    }

    public Optional<String> optional(String dotted) {
        JsonNode n = at(dotted);
        if (n == null || n.isMissingNode() || n.isNull()) return Optional.empty();
        String v = n.asText();
        return v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    public int intAt(String dotted, int defaultValue) {
        JsonNode n = at(dotted);
        return (n == null || n.isMissingNode()) ? defaultValue : n.asInt(defaultValue);
    }

    public boolean boolAt(String dotted, boolean defaultValue) {
        JsonNode n = at(dotted);
        return (n == null || n.isMissingNode()) ? defaultValue : n.asBoolean(defaultValue);
    }

    private JsonNode at(String dotted) {
        JsonNode n = root;
        for (String part : dotted.split("\\.")) {
            if (n == null) return null;
            n = n.get(part);
        }
        return n;
    }
}
