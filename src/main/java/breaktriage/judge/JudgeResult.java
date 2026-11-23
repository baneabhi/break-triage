package breaktriage.judge;

import java.util.Map;

public record JudgeResult(Map<String, JudgeScore> scores,
                          long inputTokens, long outputTokens, long latencyMs) {

    public double average() {
        return scores.values().stream().mapToInt(JudgeScore::score).average().orElse(0);
    }

    public int lowest() {
        return scores.values().stream().mapToInt(JudgeScore::score).min().orElse(0);
    }
}