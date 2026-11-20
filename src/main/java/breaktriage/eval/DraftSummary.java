package breaktriage.eval;

import breaktriage.draft.DraftResult;
import breaktriage.draft.DraftStatus;
import breaktriage.model.Category;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DraftSummary(Map<DraftStatus, Long> byStatus, int unsafeDrafts,
                           long inputTokens, long outputTokens, double avgLatencyMs) {

    public long errors() {
        return byStatus.getOrDefault(DraftStatus.ERROR, 0L);
    }

    public static DraftSummary of(List<PipelineOutcome> outcomes) {
        Map<DraftStatus, Long> byStatus = new EnumMap<>(DraftStatus.class);
        int unsafe = 0, calls = 0;
        long in = 0, out = 0, latency = 0;

        for (PipelineOutcome o : outcomes) {
            DraftResult d = o.draft();
            byStatus.merge(d.status(), 1L, Long::sum);
            in += d.inputTokens();
            out += d.outputTokens();
            if (d.status() != DraftStatus.SKIPPED) {
                latency += d.latencyMs();
                calls++;
            }
            if (d.status() == DraftStatus.DRAFTED
                    && o.expected().category() == Category.NEEDS_HUMAN) {
                unsafe++;
            }
        }
        double avg = calls == 0 ? 0 : (double) latency / calls;
        return new DraftSummary(byStatus, unsafe, in, out, avg);
    }
}