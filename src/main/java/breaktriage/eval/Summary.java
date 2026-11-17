package breaktriage.eval;

import breaktriage.triage.DecisionSource;
import breaktriage.triage.TriageResult;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record Summary(int total, int errored, int correct, int unsafe,
                      long inputTokens, long outputTokens, double avgLatencyMs,
                      Map<DecisionSource, Long> bySource) {

    public int scored() { return total - errored; }

    public double accuracy() { return scored() == 0 ? 0 : (double) correct / scored(); }

    public static Summary of(List<CaseOutcome> outcomes) {
        int errored = 0, correct = 0, unsafe = 0, calls = 0;
        long in = 0, out = 0, latency = 0;
        Map<DecisionSource, Long> bySource = new EnumMap<>(DecisionSource.class);

        for (CaseOutcome o : outcomes) {
            TriageResult r = o.result();
            bySource.merge(r.source(), 1L, Long::sum);
            in += r.inputTokens();
            out += r.outputTokens();
            if (o.errored()) {
                errored++;
                continue;
            }
            if (r.source() != DecisionSource.RULE) {
                latency += r.latencyMs();
                calls++;
            }
            if (o.correct()) correct++;
            if (o.unsafe()) unsafe++;
        }
        double avg = calls == 0 ? 0 : (double) latency / calls;
        return new Summary(outcomes.size(), errored, correct, unsafe, in, out, avg, bySource);
    }
}