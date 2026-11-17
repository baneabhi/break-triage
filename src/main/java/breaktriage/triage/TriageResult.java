package breaktriage.triage;

import breaktriage.model.Category;

public record TriageResult(Category category, String runbookId, String action,
                            String rationale, DecisionSource source,
                            long inputTokens, long outputTokens, long latencyMs) {}