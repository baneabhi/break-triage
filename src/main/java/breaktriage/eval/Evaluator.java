package breaktriage.eval;

import breaktriage.model.EvalCase;
import breaktriage.triage.TriageService;
import java.util.ArrayList;
import java.util.List;

public final class Evaluator {

    private Evaluator() {}

    public static List<CaseOutcome> run(List<EvalCase> cases, TriageService service) {
        List<CaseOutcome> outcomes = new ArrayList<>();
        for (EvalCase c : cases) {
            outcomes.add(new CaseOutcome(c.id(), c.expected(), service.triage(c.input())));
        }
        return outcomes;
    }
}