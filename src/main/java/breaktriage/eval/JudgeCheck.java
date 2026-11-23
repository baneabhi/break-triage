package breaktriage.eval;

import breaktriage.judge.DraftJudge;
import breaktriage.judge.JudgePromptBuilder;
import breaktriage.judge.JudgeResult;
import breaktriage.judge.JudgeScore;
import breaktriage.llm.ClaudeClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JudgeCheck {

    public record PlantedCase(String id, String facts, String draft, String expectLow, boolean control) {}

    public static void main(String[] args) throws Exception {
        String model = System.getenv().getOrDefault("BREAK_JUDGE_MODEL", "claude-sonnet-5-5");
        boolean thinkingOff = !"default".equalsIgnoreCase(System.getenv("BREAK_JUDGE_THINKING"));
        DraftJudge judge = new DraftJudge(new ClaudeClient(model, thinkingOff));

        List<PlantedCase> cases;
        try (InputStream in = JudgeCheck.class.getResourceAsStream("/data/judge-planted.json")) {
            cases = new ObjectMapper().readValue(in, new TypeReference<List<PlantedCase>>() {});
        }

        System.out.printf("Judge model: %s   Judge prompt: %s   Thinking: %s%n",
                model, JudgePromptBuilder.VERSION, thinkingOff ? "off" : "default");

        int passed = 0;
        for (PlantedCase c : cases) {
            Optional<JudgeResult> result = judge.judge(c.facts(), c.draft());
            if (result.isEmpty()) {
                System.out.printf("%s  JUDGE FAILED (no valid scores)%n", c.id());
                continue;
            }
            Map<String, JudgeScore> scores = result.get().scores();
            boolean ok = c.control()
                    ? scores.values().stream().allMatch(s -> s.score() >= 4)
                    : scores.get(c.expectLow()).score() <= 2;
            if (ok) {
                passed++;
            }
            String line = scores.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue().score())
                    .collect(Collectors.joining(" "));
            System.out.printf("%s  %s  %s%n", c.id(), ok ? "PASS" : "FAIL", line);
            if (!c.control()) {
                System.out.printf("    %s: %s%n", c.expectLow(), scores.get(c.expectLow()).reason());
            }
        }
        System.out.printf("Planted checks passed: %d/%d%n", passed, cases.size());
        if (passed != cases.size()) {
            System.exit(1);
        }
    }
}