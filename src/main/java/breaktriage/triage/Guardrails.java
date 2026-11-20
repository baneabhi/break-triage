package breaktriage.triage;

import breaktriage.model.BreakRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Guardrails {

    public static final BigDecimal NOTIONAL_LIMIT = new BigDecimal("5000000");

    private Guardrails() {}

    /** Returns a reason if the break must go to a human without asking the model. */
    public static Optional<String> mustEscalate(BreakRecord b) {
        if (exceedsNotionalLimit(b)) {
            return Optional.of("Notional " + b.notional().toPlainString()
                    + " is above the limit of " + NOTIONAL_LIMIT.toPlainString());
        }
        List<String> differing = differingFields(b);
        if (differing.size() > 1) {
            return Optional.of("More than one field differs: " + String.join(", ", differing));
        }
        return Optional.empty();
    }

    public static List<String> differingFields(BreakRecord b) {
        List<String> fields = new ArrayList<>();
        if (b.theirQuantity() != null && b.theirQuantity().longValue() != b.ourQuantity()) {
            fields.add("quantity");
        }
        if (b.theirPrice() != null && b.theirPrice().compareTo(b.ourPrice()) != 0) {
            fields.add("price");
        }
        if (b.theirSettleDate() != null && !b.theirSettleDate().equals(b.ourSettleDate())) {
            fields.add("settlementDate");
        }
        return fields;
    }

    /** Absolute price difference as a percentage of our price, to 4 decimal places. */
    public static Optional<BigDecimal> priceDifferencePct(BreakRecord b) {
        if (b.theirPrice() == null) {
            return Optional.empty();
        }
        return Optional.of(b.theirPrice().subtract(b.ourPrice()).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(b.ourPrice(), 4, RoundingMode.HALF_UP));
    }
    
    public static boolean exceedsNotionalLimit(BreakRecord b) {
        var notional = b.notional();
        if (notional == null) {
            return false;   // can't calculate it, so it can't exceed the limit
        }
        return (b.notional().compareTo(NOTIONAL_LIMIT) > 0);
    }
}