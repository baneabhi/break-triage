package breaktriage.draft;

import breaktriage.llm.LlmClient;
import breaktriage.llm.LlmResponse;
import breaktriage.llm.LlmUnavailableException;
import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import breaktriage.triage.TriageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageDrafter {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_WORDS = 150;
    private static final Set<String> MESSAGE_ACTIONS =
            Set.of("CONTACT_COUNTERPARTY", "REQUEST_SSI", "CHASE_CONFIRMATION");
    private static final List<String> FORBIDDEN = List.of(
            "we will cancel", "we will amend", "we agree", "we accept",
            "runbook", "as an ai", "language model");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:,\\d{3})*(?:\\.\\d+)?");

    record ModelDraft(String subject, String body) {}

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public MessageDrafter(LlmClient llm) { this.llm = llm; }

    /** Gate: only drafts for outcomes where the counterparty needs to be contacted. */
    public static boolean shouldDraft(TriageResult t) {
        return t.category() != Category.NEEDS_HUMAN
                && t.action() != null
                && MESSAGE_ACTIONS.contains(t.action());
    }

    public DraftResult draft(BreakRecord b, TriageResult t) {
        if (!shouldDraft(t)) {
            return DraftResult.skipped("No counterparty message for " + t.category() + " / " + t.action());
        }

        String system = DraftPromptBuilder.system();
        String facts = DraftPromptBuilder.facts(b, t);
        long in = 0, out = 0, ms = 0;
        String problem = "no attempt made";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LlmResponse r;
            try {
                r = llm.complete(system, facts);                
            } catch (LlmUnavailableException e) {
                return new DraftResult(DraftStatus.ERROR, null, null,
                        "Model unavailable: " + e.getMessage(), in, out, ms);
            }
            in += r.inputTokens();
            out += r.outputTokens();
            ms += r.latencyMs();
            try {
                ModelDraft d = parse(r.text());
                String issue = check(d, b, facts);
                if (issue == null) {
                    return new DraftResult(DraftStatus.DRAFTED, d.subject(), d.body(), "ok", in, out, ms);
                }
                problem = issue;
            } catch (IllegalArgumentException | JsonProcessingException e) {
                problem = "invalid answer: " + e.getMessage();
            }
        }
        return new DraftResult(DraftStatus.REJECTED, null, null,
                "Draft rejected after " + MAX_ATTEMPTS + " attempts: " + problem, in, out, ms);
    }

    private ModelDraft parse(String text) throws JsonProcessingException {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("no JSON object in answer");
        }
        return mapper.readValue(text.substring(start, end + 1), ModelDraft.class);
    }

    /** Returns null if the draft passes, otherwise the reason it failed. */
    static String check(ModelDraft d, BreakRecord b, String facts) {
        if (d.subject() == null || d.subject().isBlank() || d.body() == null || d.body().isBlank()) {
            return "subject or body is empty";
        }
        if (!d.body().contains(b.tradeId())) {
            return "body does not mention trade ID " + b.tradeId();
        }
        if (d.body().trim().split("\\s+").length > MAX_WORDS) {
            return "body is longer than " + MAX_WORDS + " words";
        }
        String text = d.subject() + "\n" + d.body();
        String lower = text.toLowerCase();
        for (String phrase : FORBIDDEN) {
            if (lower.contains(phrase)) {
                return "contains forbidden phrase: " + phrase;
            }
        }
        Set<String> allowed = numbersIn(facts);
        for (String n : numbersIn(text)) {
            if (!allowed.contains(n)) {
                return "number not in the facts: " + n;
            }
        }
        return null;
    }

    /** All numbers in the text, normalised so 10,000 and 10000.0 compare equal. */
    static Set<String> numbersIn(String text) {
        Set<String> found = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group().replace(",", "");
            found.add(new BigDecimal(raw).stripTrailingZeros().toPlainString());
        }
        return found;
    }
}