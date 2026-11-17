package breaktriage.model;

import java.math.BigDecimal;

/**
 * One settlement break as it arrives from the matching system. All data is synthetic.
 * "our" fields come from our booking system, "their" fields from the counterparty.
 * Their fields are null when the counterparty has not confirmed the trade.
 */
public record BreakRecord(
        String breakId,
        String tradeId,
        String counterparty,
        String instrument,
        String side,
        long ourQuantity,
        Long theirQuantity,
        BigDecimal ourPrice,
        BigDecimal theirPrice,
        String currency,
        String ourSettleDate,
        String theirSettleDate,
        String note) {

    /** Notional in our terms. Calculated in code, never by the model. */
    public BigDecimal notional() {
        return ourPrice.multiply(BigDecimal.valueOf(ourQuantity));
    }
}
