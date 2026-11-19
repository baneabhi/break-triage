package breaktriage.data;

import breaktriage.model.EvalCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the synthetic eval cases and the runbooks from the classpath. */
public final class DataLoader {

    public static final List<String> RUNBOOK_IDS =
            List.of("RB-001", "RB-002", "RB-003", "RB-004", "RB-005", "RB-006");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataLoader() {}

    public static List<EvalCase> loadEvalCases() throws IOException {
        return loadEvalCases("eval-cases.json");
    }

    public static List<EvalCase> loadEvalCases(String fileName) throws IOException {
        try (InputStream in = open("/data/" + fileName)) {
            return MAPPER.readValue(in, new TypeReference<List<EvalCase>>() {});
        }
    }

    /** Runbook text keyed by runbook id, in id order. */
    public static Map<String, String> loadRunbooks() throws IOException {
        Map<String, String> runbooks = new LinkedHashMap<>();
        for (String id : RUNBOOK_IDS) {
            runbooks.put(id, readText("/runbooks/" + id + ".md"));
        }
        return runbooks;
    }

    /** Rules that apply to every break, whichever runbook matches. */
    public static String loadGeneralRules() throws IOException {
        return readText("/runbooks/GENERAL.md");
    }

    private static String readText(String path) throws IOException {
        try (InputStream in = open(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream open(String path) {
        InputStream in = DataLoader.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Resource not found on classpath: " + path);
        }
        return in;
    }
}
