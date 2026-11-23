package breaktriage.eval;

import breaktriage.draft.DraftPromptBuilder;
import breaktriage.draft.DraftResult;
import breaktriage.draft.DraftStatus;
import breaktriage.judge.DraftJudge;
import breaktriage.judge.JudgeResult;
import breaktriage.judge.JudgeSummary;
import breaktriage.llm.LlmUnavailableException;
import breaktriage.model.BreakRecord;
import breaktriage.model.EvalCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EvalJudge {

    private EvalJudge() {}

    public static JudgeSummary run(List<EvalCase> cases, List<PipelineOutcome> outcomes, DraftJudge judge) {
        Map<String, BreakRecord> inputs = new HashMap<>();
        for (EvalCase c : cases) {
            inputs.put(c.id(), c.input());
        }

        List<JudgeSummary.Item> items = new ArrayList<>();
        int failed = 0;

        for (PipelineOutcome outcome : outcomes) {
            DraftResult draft = outcome.draft();
            if (draft.status() != DraftStatus.DRAFTED) {
                continue;   // nothing to judge
            }
            BreakRecord input = inputs.get(outcome.caseId());
            String facts = DraftPromptBuilder.facts(input, outcome.triage());
            String draftText = "Subject: " + draft.subject() + "\n\n" + draft.body();
            try {
                Optional<JudgeResult> result = judge.judge(facts, draftText);
                if (result.isPresent()) {
                    items.add(new JudgeSummary.Item(outcome.caseId(), result.get()));
                } else {
                    failed++;
                }
            } catch (LlmUnavailableException e) {
                failed++;
            }
        }
        return new JudgeSummary(items, failed);
    }
}