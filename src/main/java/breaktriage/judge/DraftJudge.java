package breaktriage.judge;

import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DraftJudge {

    public static final List<String> CRITERIA =
            List.of("accuracy", "clarity", "ask", "tone", "commitments");

    private static final int MAX_ATTEMPTS = 2;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llm;

    public DraftJudge(LlmClient llm) {
        this.llm = llm;
    }

    /** Empty means the judge gave no valid scores after retrying. An unreachable API throws LlmUnavailableException. */
    public Optional<JudgeResult> judge(String facts, String draft) {
        String system = JudgePromptBuilder.system();
        String user = JudgePromptBuilder.user(facts, draft);
        long in = 0;
        long out = 0;
        long ms = 0;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LlmResponse r = llm.complete(system, user);
            in += r.inputTokens();
            out += r.outputTokens();
            ms += r.latencyMs();
            Optional<Map<String, JudgeScore>> parsed = parse(r.text());
            if (parsed.isPresent()) {
                return Optional.of(new JudgeResult(parsed.get(), in, out, ms));
            }
        }
        return Optional.empty();
    }

    static Optional<Map<String, JudgeScore>> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(text.substring(start, end + 1));
            Map<String, JudgeScore> scores = new LinkedHashMap<>();
            for (String criterion : CRITERIA) {
                JsonNode node = root.get(criterion);
                if (node == null || !node.path("score").isInt()) {
                    return Optional.empty();
                }
                int score = node.get("score").asInt();
                if (score < 1 || score > 5) {
                    return Optional.empty();
                }
                scores.put(criterion, new JudgeScore(score, node.path("reason").asText("")));
            }
            return Optional.of(scores);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}