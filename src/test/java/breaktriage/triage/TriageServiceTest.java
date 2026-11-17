package breaktriage.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import breaktriage.data.DataLoader;
import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.llm.LlmUnavailableException;
import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TriageServiceTest {

    private static BreakRecord input(String caseId) throws IOException {
        return DataLoader.loadEvalCases().stream()
                .filter(c -> c.id().equals(caseId)).findFirst().orElseThrow().input();
    }

    @Test
    void largeNotionalIsEscalatedWithoutCallingTheModel() throws IOException {
        LlmClient mustNotBeCalled = (system, user) -> {
            throw new AssertionError("the model should not be called");
        };
        TriageResult r = new TriageService(mustNotBeCalled).triage(input("CASE-025"));
        assertEquals(Category.NEEDS_HUMAN, r.category());
        assertEquals(DecisionSource.RULE, r.source());
    }

    @Test
    void validModelAnswerIsAccepted() throws IOException {
        LlmClient fake = (system, user) -> new LlmResponse(
                "{\"category\":\"QUANTITY_MISMATCH\",\"runbookId\":\"RB-001\","
                        + "\"action\":\"CONTACT_COUNTERPARTY\",\"rationale\":\"Quantities differ.\"}",
                100, 20, 5);
        TriageResult r = new TriageService(fake).triage(input("CASE-001"));
        assertEquals(Category.QUANTITY_MISMATCH, r.category());
        assertEquals(DecisionSource.MODEL, r.source());
    }

    @Test
    void wrongRunbookIsRetriedThenEscalated() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient fake = (system, user) -> {
            calls.incrementAndGet();
            return new LlmResponse(
                    "{\"category\":\"PRICE_MISMATCH\",\"runbookId\":\"RB-001\","
                            + "\"action\":\"X\",\"rationale\":\"Wrong runbook.\"}", 100, 20, 5);
        };
        TriageResult r = new TriageService(fake).triage(input("CASE-001"));
        assertEquals(2, calls.get());
        assertEquals(DecisionSource.FALLBACK, r.source());
        assertEquals(Category.NEEDS_HUMAN, r.category());
    }

    @Test
    void inventedActionIsRetriedThenEscalated() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient fake = (system, user) -> {
            calls.incrementAndGet();
            return new LlmResponse(
                    "{\"category\":\"QUANTITY_MISMATCH\",\"runbookId\":\"RB-001\","
                            + "\"action\":\"DELETE_TRADE\",\"rationale\":\"Looks wrong.\"}", 100, 20, 5);
        };
        TriageResult r = new TriageService(fake).triage(input("CASE-001"));
        assertEquals(2, calls.get());
        assertEquals(DecisionSource.FALLBACK, r.source());
    }

    @Test
    void apiFailureIsEscalatedWithoutExtraRetries() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient failing = (system, user) -> {
            calls.incrementAndGet();
            throw new LlmUnavailableException("rate limited", null);
        };
        TriageResult r = new TriageService(failing).triage(input("CASE-001"));
        assertEquals(1, calls.get());
        assertEquals(DecisionSource.ERROR, r.source());
        assertEquals(Category.NEEDS_HUMAN, r.category());
    }
}