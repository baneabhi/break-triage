package breaktriage.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import breaktriage.data.DataLoader;
import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.llm.LlmUnavailableException;
import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import breaktriage.triage.DecisionSource;
import breaktriage.triage.TriageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MessageDrafterTest {

    private static final String GOOD_BODY =
            "Dear Operations,\n\nWe are writing about trade T-100001 in TST001. "
                    + "Our record shows a quantity of 10000 and yours shows 1000. "
                    + "Please check your record and confirm the correct quantity.\n\n"
                    + "Regards,\nSettlement Operations";

    private static BreakRecord input() throws IOException {
        return DataLoader.loadEvalCases().stream()
                .filter(c -> c.id().equals("CASE-001")).findFirst().orElseThrow().input();
    }

    private static TriageResult triage(Category category, String action) {
        return new TriageResult(category, category.runbookId(), action, "r",
                DecisionSource.MODEL, 0, 0, 0);
    }

    private static String reply(String subject, String body) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("subject", subject, "body", body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void gateAllowsOnlyCounterpartyActions() {
        assertTrue(MessageDrafter.shouldDraft(triage(Category.QUANTITY_MISMATCH, "CONTACT_COUNTERPARTY")));
        assertTrue(MessageDrafter.shouldDraft(triage(Category.MISSING_SSI, "REQUEST_SSI")));
        assertTrue(MessageDrafter.shouldDraft(triage(Category.UNCONFIRMED_TRADE, "CHASE_CONFIRMATION")));
        assertFalse(MessageDrafter.shouldDraft(triage(Category.QUANTITY_MISMATCH, "AMEND_OUR_BOOKING")));
        assertFalse(MessageDrafter.shouldDraft(triage(Category.PRICE_MISMATCH, "ACCEPT_WITHIN_TOLERANCE")));
        assertFalse(MessageDrafter.shouldDraft(triage(Category.NEEDS_HUMAN, "ESCALATE_TO_HUMAN")));
    }

    @Test
    void skippedCaseNeverCallsTheModel() throws IOException {
        LlmClient mustNotBeCalled = (system, user) -> {
            throw new AssertionError("the model should not be called");
        };
        DraftResult r = new MessageDrafter(mustNotBeCalled)
                .draft(input(), triage(Category.NEEDS_HUMAN, "ESCALATE_TO_HUMAN"));
        assertEquals(DraftStatus.SKIPPED, r.status());
    }

    @Test
    void goodDraftIsAccepted() throws IOException {
        LlmClient fake = (system, user) -> new LlmResponse(
                reply("Trade T-100001: quantity break", GOOD_BODY), 100, 50, 5);
        DraftResult r = new MessageDrafter(fake)
                .draft(input(), triage(Category.QUANTITY_MISMATCH, "CONTACT_COUNTERPARTY"));
        assertEquals(DraftStatus.DRAFTED, r.status());
    }

    @Test
    void inventedNumberIsRetriedThenRejected() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient fake = (system, user) -> {
            calls.incrementAndGet();
            return new LlmResponse(reply("Trade T-100001: quantity break",
                    GOOD_BODY.replace("1000.", "1200.")), 100, 50, 5);
        };
        DraftResult r = new MessageDrafter(fake)
                .draft(input(), triage(Category.QUANTITY_MISMATCH, "CONTACT_COUNTERPARTY"));
        assertEquals(2, calls.get());
        assertEquals(DraftStatus.REJECTED, r.status());
    }

    @Test
    void promisingToCancelIsRejected() throws IOException {
        LlmClient fake = (system, user) -> new LlmResponse(reply("Trade T-100001",
                GOOD_BODY.replace("Please check", "We will cancel our booking. Please check")), 100, 50, 5);
        DraftResult r = new MessageDrafter(fake)
                .draft(input(), triage(Category.QUANTITY_MISMATCH, "CONTACT_COUNTERPARTY"));
        assertEquals(DraftStatus.REJECTED, r.status());
    }

    @Test
    void apiFailureGivesErrorWithoutRetrying() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient failing = (system, user) -> {
            calls.incrementAndGet();
            throw new LlmUnavailableException("rate limited", null);
        };
        DraftResult r = new MessageDrafter(failing)
                .draft(input(), triage(Category.QUANTITY_MISMATCH, "CONTACT_COUNTERPARTY"));
        assertEquals(1, calls.get());
        assertEquals(DraftStatus.ERROR, r.status());
    }
}