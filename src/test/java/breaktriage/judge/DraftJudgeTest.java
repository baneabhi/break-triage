package breaktriage.judge;

import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DraftJudgeTest {

    private static String json(int accuracy, int clarity, int ask, int tone, int commitments) {
        return """
                {"accuracy":{"score":%d,"reason":"r"},"clarity":{"score":%d,"reason":"r"},\
                "ask":{"score":%d,"reason":"r"},"tone":{"score":%d,"reason":"r"},\
                "commitments":{"score":%d,"reason":"r"}}
                """.formatted(accuracy, clarity, ask, tone, commitments);
    }

    private static LlmClient always(String text) {
        return (system, user) -> new LlmResponse(text, 10, 5, 100);
    }

    @Test
    void validScores_areParsedAndSummarised() {
        DraftJudge judge = new DraftJudge(always(json(5, 4, 3, 4, 4)));

        JudgeResult result = judge.judge("facts", "draft").orElseThrow();

        assertEquals(3, result.scores().get("ask").score());
        assertEquals(4.0, result.average(), 0.0001);
        assertEquals(3, result.lowest());
    }

    @Test
    void textAroundTheJson_isIgnored() {
        DraftJudge judge = new DraftJudge(always("Here you go: " + json(4, 4, 4, 4, 4) + " Thanks."));

        assertTrue(judge.judge("facts", "draft").isPresent());
    }

    @Test
    void missingCriterion_isRetriedOnce_thenEmpty() {
        AtomicInteger calls = new AtomicInteger();
        String missing = """
                {"accuracy":{"score":5,"reason":"r"},"clarity":{"score":5,"reason":"r"},\
                "ask":{"score":5,"reason":"r"},"tone":{"score":5,"reason":"r"}}
                """;
        DraftJudge judge = new DraftJudge((system, user) -> {
            calls.incrementAndGet();
            return new LlmResponse(missing, 10, 5, 100);
        });

        Optional<JudgeResult> result = judge.judge("facts", "draft");

        assertTrue(result.isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void scoreOutOfRange_isRejected() {
        DraftJudge judge = new DraftJudge(always(json(6, 4, 4, 4, 4)));

        assertTrue(judge.judge("facts", "draft").isEmpty());
    }

    @Test
    void nonIntegerScore_isRejected() {
        String decimal = json(4, 4, 4, 4, 4).replace("\"score\":4", "\"score\":4.5");
        DraftJudge judge = new DraftJudge(always(decimal));

        assertTrue(judge.judge("facts", "draft").isEmpty());
    }

    @Test
    void invalidThenValid_succeeds_andTokensAreSummed() {
        AtomicInteger calls = new AtomicInteger();
        DraftJudge judge = new DraftJudge((system, user) -> {
            String text = calls.incrementAndGet() == 1 ? "not json" : json(5, 5, 5, 5, 5);
            return new LlmResponse(text, 10, 5, 100);
        });

        JudgeResult result = judge.judge("facts", "draft").orElseThrow();

        assertEquals(20, result.inputTokens());
        assertEquals(10, result.outputTokens());
        assertEquals(200, result.latencyMs());
    }
}