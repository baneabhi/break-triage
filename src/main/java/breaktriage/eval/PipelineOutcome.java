package breaktriage.eval;

import breaktriage.draft.DraftResult;
import breaktriage.model.Expected;
import breaktriage.triage.TriageResult;

public record PipelineOutcome(String caseId, Expected expected,
                              TriageResult triage, DraftResult draft) {

    public CaseOutcome classification() {
        return new CaseOutcome(caseId, expected, triage);
    }
}