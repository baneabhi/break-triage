package breaktriage.draft;

public record DraftResult(DraftStatus status, String subject, String body, String note,
                          long inputTokens, long outputTokens, long latencyMs) {

    static DraftResult skipped(String reason) {
        return new DraftResult(DraftStatus.SKIPPED, null, null, reason, 0, 0, 0);
    }
}