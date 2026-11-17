package breaktriage.model;

import java.util.List;

public enum Category {
    QUANTITY_MISMATCH("RB-001", "CONTACT_COUNTERPARTY", "AMEND_OUR_BOOKING"),
    PRICE_MISMATCH("RB-002", "ACCEPT_WITHIN_TOLERANCE", "CONTACT_COUNTERPARTY", "AMEND_OUR_BOOKING"),
    MISSING_SSI("RB-003", "REQUEST_SSI"),
    UNCONFIRMED_TRADE("RB-004", "CHASE_CONFIRMATION"),
    DUPLICATE_TRADE("RB-005", "CANCEL_DUPLICATE_PENDING_TRADER"),
    SETTLE_DATE_MISMATCH("RB-006", "CONTACT_COUNTERPARTY", "AMEND_OUR_BOOKING"),
    NEEDS_HUMAN(null, "ESCALATE_TO_HUMAN");

    private final String runbookId;
    private final List<String> allowedActions;

    Category(String runbookId, String... allowedActions) {
        this.runbookId = runbookId;
        this.allowedActions = List.of(allowedActions);
    }

    public String runbookId() { return runbookId; }

    public List<String> allowedActions() { return allowedActions; }
}