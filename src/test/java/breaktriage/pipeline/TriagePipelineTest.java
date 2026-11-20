package breaktriage.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import breaktriage.data.DataLoader;
import breaktriage.draft.DraftStatus;
import breaktriage.draft.MessageDrafter;
import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import breaktriage.triage.DecisionSource;
import breaktriage.triage.TriageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriagePipelineTest {

    private static final String DRAFT_BODY =
            "Dear Operations,\n\nWe are writing about trade T-100001 in TST001. "
                    + "Our record shows a quantity of 10000 and yours shows 1000. "
                    + "Please check your record and confirm the correct quantity.\n\n"
                    + "Regards,\nSettlement Operations";

    private static final String CLASSIFICATION =
            "{\"category\":\"QUANTITY_MISMATCH\",\"runbookId\":\"RB-001\","
                    + "\"action\":\"CONTACT_COUNTERPARTY\",\"rationale\":\"Quantities differ.\"}";

    private static BreakRecord input(String caseId) throws IOException {
        return DataLoader.loadEvalCases().stream()
                .filter(c -> c.id().equals(caseId)).findFirst().orElseThrow().input();
    }

    private static String draftReply() {
        try {
            return new ObjectMapper().writeValueAsString(
                    Map.of("subject", "Trade T-100001: quantity break", "body", DRAFT_BODY));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void classifiedBreakGetsADraft() throws IOException {
        LlmClient fake = (system, user) -> system.startsWith("You draft")
                ? new LlmResponse(draftReply(), 100, 50, 5)
                : new LlmResponse(CLASSIFICATION, 100, 20, 5);

        TriagePipeline pipeline = new TriagePipeline(new TriageService(fake), new MessageDrafter(fake));
        PipelineResult r = pipeline.run(input("CASE-001"));

        assertEquals(Category.QUANTITY_MISMATCH, r.triage().category());
        assertEquals(DraftStatus.DRAFTED, r.draft().status());
    }

    @Test
    void ruleEscalationNeverReachesEitherModelCall() throws IOException {
        LlmClient mustNotBeCalled = (system, user) -> {
            throw new AssertionError("no model call expected");
        };
        TriagePipeline pipeline = new TriagePipeline(
                new TriageService(mustNotBeCalled), new MessageDrafter(mustNotBeCalled));
        PipelineResult r = pipeline.run(input("CASE-025"));

        assertEquals(DecisionSource.RULE, r.triage().source());
        assertEquals(DraftStatus.SKIPPED, r.draft().status());
    }
}