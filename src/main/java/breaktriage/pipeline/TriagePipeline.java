package breaktriage.pipeline;

import breaktriage.draft.DraftResult;
import breaktriage.draft.MessageDrafter;
import breaktriage.model.BreakRecord;
import breaktriage.triage.TriageResult;
import breaktriage.triage.TriageService;

public final class TriagePipeline {

    private final TriageService triageService;
    private final MessageDrafter drafter;

    public TriagePipeline(TriageService triageService, MessageDrafter drafter) {
        this.triageService = triageService;
        this.drafter = drafter;
    }

    public PipelineResult run(BreakRecord b) {
        TriageResult triage = triageService.triage(b);   // call 1 (or a code rule)
        DraftResult draft = drafter.draft(b, triage);    // gate, then call 2
        return new PipelineResult(triage, draft);
    }
}