package breaktriage.triage;

import breaktriage.data.DataLoader;
import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.llm.LlmUnavailableException;
import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class TriageService {

    private static final int MAX_ATTEMPTS = 2;

    record ModelAnswer(String category, String runbookId, String action, String rationale) {}

    private final LlmClient llm;
    private final String systemPrompt;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public TriageService(LlmClient llm) throws IOException {
        this.llm = llm;
        this.systemPrompt = PromptBuilder.system(
                DataLoader.loadGeneralRules(), DataLoader.loadRunbooks());
    }

    public TriageResult triage(BreakRecord b) {
        Optional<String> escalation = Guardrails.mustEscalate(b);
        if (escalation.isPresent()) {
            return new TriageResult(Category.NEEDS_HUMAN, null, "ESCALATE_TO_HUMAN",
                    escalation.get(), DecisionSource.RULE, 0, 0, 0);
        }

        String user = PromptBuilder.user(b);
        long in = 0, out = 0, ms = 0;
        String problem = "no attempt made";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LlmResponse r;
            try {
                r = llm.complete(systemPrompt, user);
            } catch (LlmUnavailableException e) {
                return new TriageResult(Category.NEEDS_HUMAN, null, "ESCALATE_TO_HUMAN",
                        "Model unavailable: " + e.getMessage(), DecisionSource.ERROR, in, out, ms);
            }
            in += r.inputTokens();
            out += r.outputTokens();
            ms += r.latencyMs();
            try {
                ModelAnswer a = parse(r.text());
                Category c = Category.valueOf(a.category());
                if (!Objects.equals(c.runbookId(), a.runbookId())) {
                    problem = "runbookId " + a.runbookId() + " does not belong to category " + c;
                } else if (!c.allowedActions().contains(a.action())) {
                    problem = "action " + a.action() + " is not allowed for " + c;
                } else {
                    return new TriageResult(c, c.runbookId(), a.action(), a.rationale(),
                            DecisionSource.MODEL, in, out, ms);
                }
                problem = "runbookId " + a.runbookId() + " does not belong to category " + c;
            } catch (IllegalArgumentException | JsonProcessingException e) {
                problem = "invalid answer: " + e.getMessage()+ " | raw: " + abbreviate(r.text());
            }
        }

        return new TriageResult(Category.NEEDS_HUMAN, null, "ESCALATE_TO_HUMAN",
                "Model answer rejected after " + MAX_ATTEMPTS + " attempts: " + problem,
                DecisionSource.FALLBACK, in, out, ms);
    }

    private ModelAnswer parse(String text) throws JsonProcessingException {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("no JSON object in answer");
        }
        ModelAnswer a = mapper.readValue(text.substring(start, end + 1), ModelAnswer.class);
        if (a.category() == null) {
            throw new IllegalArgumentException("missing category");
        }
        return a;
    }

    private static String abbreviate(String s) {
        String flat = s.replace('\n', ' ');
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "...";
    }
}