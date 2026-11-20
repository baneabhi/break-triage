package breaktriage.draft;

import breaktriage.model.BreakRecord;
import breaktriage.model.Category;
import breaktriage.triage.TriageResult;
import java.math.BigDecimal;

public final class DraftPromptBuilder {

    public static final String VERSION = "d2";

    private DraftPromptBuilder() {}

    public static String system() {
        return """
                You draft a short email from a bank's settlement operations team to a counterparty's operations desk about a trade settlement break.
                Use only the facts provided. Do not add numbers, dates or names that are not in the facts.
                Do not promise to cancel, amend, agree or accept anything. 
                Describe the trade from our point of view ("we bought" or "we sold"), never as a bare BUY or SELL.
                State the issue and make the request.
                Do not mention internal rules, runbooks, categories, systems or AI.
                Write plain text, polite and direct, with a body of at most 100 words.
                End the body with "Regards," on one line and "Settlement Operations" on the next.

                Respond with one JSON object and nothing else:
                {"subject": "...", "body": "..."}
                """;
    }

    public static String facts(BreakRecord b, TriageResult t) {
        return """
                <facts>
                counterparty: %s
                tradeId: %s
                instrument: %s
                our side: %s
                issue: %s
                our quantity: %s
                their quantity: %s
                our price: %s
                their price: %s
                our settlement date: %s
                their settlement date: %s
                requested action: %s
                </facts>
                """.formatted(
                b.counterparty(), b.tradeId(), b.instrument(), sideText(b.side()),
                issueText(t.category()),
                show(b.ourQuantity()), show(b.theirQuantity()),
                show(b.ourPrice()), show(b.theirPrice()),
                show(b.ourSettleDate()), show(b.theirSettleDate()),
                requestText(t.action()));
    }

    private static String issueText(Category c) {
        return switch (c) {
            case QUANTITY_MISMATCH -> "the quantity on our record does not match the quantity on theirs";
            case PRICE_MISMATCH -> "the price on our record does not match the price on theirs";
            case SETTLE_DATE_MISMATCH -> "the settlement date on our record does not match theirs";
            case MISSING_SSI -> "we need your current standing settlement instructions in order to settle this trade";
            case UNCONFIRMED_TRADE -> "we have not received a confirmation for this trade";
            default -> throw new IllegalArgumentException("no counterparty message for " + c);
        };
    }

    private static String sideText(String side) {
        return "BUY".equals(side) ? "we bought" : "we sold";
    }

    private static String requestText(String action) {
        return switch (action) {
            case "CONTACT_COUNTERPARTY" -> "ask them to check their record against ours and confirm the correct value";
            case "REQUEST_SSI" -> "ask them to send their current standing settlement instructions";
            case "CHASE_CONFIRMATION" -> "ask them to confirm or affirm the trade";
            default -> throw new IllegalArgumentException("no counterparty message for action " + action);
        };
    }

    private static String show(Object value) {
        if (value == null) return "not provided";
        if (value instanceof BigDecimal d) return d.toPlainString();
        return value.toString();
    }
}