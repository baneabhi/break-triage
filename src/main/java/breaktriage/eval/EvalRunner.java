package breaktriage.eval;

import breaktriage.data.DataLoader;
import breaktriage.draft.DraftPromptBuilder;
import breaktriage.draft.DraftStatus;
import breaktriage.draft.MessageDrafter;
import breaktriage.judge.DraftJudge;
import breaktriage.llm.ClaudeClient;
import breaktriage.model.Category;
import breaktriage.model.EvalCase;
import breaktriage.pipeline.TriagePipeline;
import breaktriage.triage.PromptBuilder;
import breaktriage.triage.TriageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvalRunner {

    public static void main(String[] args) throws Exception {
        String model = System.getenv().getOrDefault("BREAK_MODEL", "claude-haiku-5-5");
        String draftModel = System.getenv().getOrDefault("BREAK_DRAFT_MODEL", model);
        double threshold = Double.parseDouble(System.getenv().getOrDefault("EVAL_THRESHOLD", "0.90"));
        boolean classifierThinkingOff = "off".equalsIgnoreCase(System.getenv("BREAK_THINKING"));
        boolean draftThinkingOff = !"default".equalsIgnoreCase(System.getenv("BREAK_DRAFT_THINKING"));
        String evalSet = System.getenv().getOrDefault("EVAL_SET", "eval-cases.json");

        RunSettings settings = new RunSettings(model, draftModel,
                        classifierThinkingOff, draftThinkingOff, evalSet);

        List<EvalCase> cases = DataLoader.loadEvalCases(evalSet);
        String only = System.getenv("EVAL_ONLY");
        if (only != null) {
            Set<String> ids = Set.of(only.split(","));
            cases = cases.stream().filter(c -> ids.contains(c.id())).toList();
        }

        TriagePipeline pipeline = new TriagePipeline(
            new TriageService(new ClaudeClient(settings.model(), settings.classifierThinkingOff())),
            new MessageDrafter(new ClaudeClient(settings.draftModel(), settings.draftThinkingOff())));
        List<PipelineOutcome> pipelineOutcomes = Evaluator.runPipeline(cases, pipeline);

        List<CaseOutcome> outcomes = pipelineOutcomes.stream()
            .map(PipelineOutcome::classification).toList();

        Summary s = Summary.of(outcomes);
        DraftSummary ds = DraftSummary.of(pipelineOutcomes);

        System.out.printf("Model: %s   Prompt: %s   Draft prompt: %s   Thinking: classify=%s, draft=%s%n",
                        model, PromptBuilder.VERSION, DraftPromptBuilder.VERSION,  settings.classifierThinking(), settings.draftThinking());
        System.out.printf("Cases: %d (API errors: %d, scored: %d)%n", s.total(), s.errored(), s.scored());
        System.out.printf("Accuracy: %d/%d = %.1f%%   (gate: %.0f%%)%n",
                s.correct(), s.scored(), s.accuracy() * 100, threshold * 100);
        System.out.printf("Unsafe automations (needed a human, got automated): %d%n", s.unsafe());
        System.out.printf("Decided by: %s%n", s.bySource());
        System.out.printf("Tokens: in=%d out=%d   avg latency per call: %.0f ms%n",
                s.inputTokens(), s.outputTokens(), s.avgLatencyMs());
        printCost(s.inputTokens() + ds.inputTokens(), s.outputTokens() + ds.outputTokens());
        printDrafts(ds, pipelineOutcomes);

        System.out.println("\nPer expected category:");
        Map<Category, int[]> byCategory = new EnumMap<>(Category.class);
        for (CaseOutcome o : outcomes) {
            if (o.errored()) continue;
            int[] t = byCategory.computeIfAbsent(o.expected().category(), k -> new int[2]);
            t[0]++;
            if (o.correct()) t[1]++;
        }
        byCategory.forEach((cat, t) -> System.out.printf("  %-22s %d/%d%n", cat, t[1], t[0]));

        System.out.println("\nFailures:");
        for (CaseOutcome o : outcomes) {
            if (o.correct()) continue;
            System.out.printf("  %s expected %s, got %s [%s]%n    %s%n", o.caseId(),
                    o.expected().category(), o.result().category(), o.result().source(),
                    o.result().rationale());
        }

        if ("on".equalsIgnoreCase(System.getenv("EVAL_JUDGE"))) {
            String judgeModel = System.getenv().getOrDefault("BREAK_JUDGE_MODEL", "claude-sonnet-5-5");
            boolean judgeThinkingOff = !"default".equalsIgnoreCase(System.getenv("BREAK_JUDGE_THINKING"));
            DraftJudge judge = new DraftJudge(new ClaudeClient(judgeModel, judgeThinkingOff));
            EvalJudge.run(cases, pipelineOutcomes, judge).print();
        }

        writeResults(settings, s, pipelineOutcomes);

        boolean pass = s.accuracy() >= threshold && s.unsafe() == 0 && s.errored() == 0
                && ds.unsafeDrafts() == 0 && ds.errors() == 0;
        System.out.println("\nRESULT: " + (pass ? "PASS" : "FAIL"));
        System.exit(pass ? 0 : 1);
    }

    /** Prices change, so they come from your environment instead of being hard-coded. */
    private static void printCost(long in, long out) {
        String pin = System.getenv("BREAK_PRICE_IN");   // USD per million input tokens
        String pout = System.getenv("BREAK_PRICE_OUT"); // USD per million output tokens
        if (pin == null || pout == null) {
            System.out.println("Cost: set BREAK_PRICE_IN and BREAK_PRICE_OUT (USD per million tokens) to see it.");
            return;
        }
        double cost = in / 1e6 * Double.parseDouble(pin)
                + out / 1e6 * Double.parseDouble(pout);
        System.out.printf("Estimated cost: $%.4f%n", cost);
    }

    private static void printDrafts(DraftSummary ds, List<PipelineOutcome> outcomes) {
        System.out.println("\nDrafts:");
        System.out.printf("  By status: %s%n", ds.byStatus());
        System.out.printf("  Unsafe drafts (drafted for a case that needed a human): %d%n", ds.unsafeDrafts());
        System.out.printf("  Tokens: in=%d out=%d   avg latency per draft call: %.0f ms%n",
                ds.inputTokens(), ds.outputTokens(), ds.avgLatencyMs());

        long draftPrintLimit = "on".equalsIgnoreCase(System.getenv("EVAL_JUDGE")) ? Long.MAX_VALUE : 3;

        outcomes.stream()
                .filter(o -> o.draft().status() == DraftStatus.DRAFTED)
                .limit(draftPrintLimit)
                .forEach(o -> System.out.printf("%n  --- %s (%s) ---%n  Subject: %s%n%s%n",
                        o.caseId(), o.triage().category(), o.draft().subject(), o.draft().body()));

        outcomes.stream()
                .filter(o -> o.draft().status() == DraftStatus.REJECTED
                        || o.draft().status() == DraftStatus.ERROR)
                .forEach(o -> System.out.printf("%n  %s draft %s: %s%n",
                        o.caseId(), o.draft().status(), o.draft().note()));
    }

    private static void writeResults(RunSettings settings, Summary s, List<PipelineOutcome> outcomes ) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("timestamp", Instant.now().toString());
        doc.put("model", settings.model());
        doc.put("promptVersion", PromptBuilder.VERSION);
        doc.put("correct", s.correct());
        doc.put("scored", s.scored());
        doc.put("accuracy", s.accuracy());
        doc.put("unsafe", s.unsafe());
        doc.put("errored", s.errored());
        doc.put("outcomes", outcomes);
        doc.put("draftPromptVersion", DraftPromptBuilder.VERSION);
        doc.put("thinkingClassify", settings.classifierThinking());
        doc.put("thinkingDraft", settings.draftThinking());

        Path dir = Path.of("results");
        Files.createDirectories(dir);
        Path file = dir.resolve("eval-" + Instant.now().toString().replace(':', '-') + ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), doc);
        System.out.println("Saved " + file);
    }

    public static String thinkingMode() {
        return "off".equalsIgnoreCase(System.getenv("BREAK_THINKING")) ? "off" : "default";
    }
}