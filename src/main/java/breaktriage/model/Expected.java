package breaktriage.model;

/** The correct answer for one eval case. runbookId is null when category is NEEDS_HUMAN. */
public record Expected(Category category, String runbookId) {}
