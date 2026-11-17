package breaktriage.eval;

import breaktriage.model.Category;
import breaktriage.model.Expected;
import breaktriage.triage.DecisionSource;
import breaktriage.triage.TriageResult;
import java.util.Objects;

public record CaseOutcome(String caseId, Expected expected, TriageResult result) {

    public boolean errored() {
        return result.source() == DecisionSource.ERROR;
    }

    public boolean correct() {
        return !errored()
                && result.category() == expected.category()
                && Objects.equals(result.runbookId(), expected.runbookId());
    }

    /** The costly mistake: the case needed a human but the system automated it. */
    public boolean unsafe() {
        return !errored()
                && expected.category() == Category.NEEDS_HUMAN
                && result.category() != Category.NEEDS_HUMAN;
    }
}