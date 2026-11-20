package breaktriage.pipeline;

import breaktriage.draft.DraftResult;
import breaktriage.triage.TriageResult;

public record PipelineResult(TriageResult triage, DraftResult draft) {}