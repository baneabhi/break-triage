package breaktriage.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import breaktriage.data.DataLoader;
import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.triage.DecisionSource;
import breaktriage.triage.TriageService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

    @Test
    void scorerCatchesALazyModel() throws IOException {
        LlmClient lazy = (system, user) -> new LlmResponse(
                "{\"category\":\"QUANTITY_MISMATCH\",\"runbookId\":\"RB-001\","
                        + "\"action\":\"CONTACT_COUNTERPARTY\",\"rationale\":\"Always the same.\"}",
                100, 20, 5);

        List<CaseOutcome> outcomes =
                Evaluator.run(DataLoader.loadEvalCases(), new TriageService(lazy));
        Summary s = Summary.of(outcomes);

        assertEquals(30, s.total());
        assertEquals(0, s.errored());
        assertEquals(7, s.correct());   // 4 real quantity cases + 3 caught by guardrails
        assertEquals(3, s.unsafe());    // cases 027, 029, 030 were automated
        assertEquals(3L, (long) s.bySource().get(DecisionSource.RULE));
    }
}