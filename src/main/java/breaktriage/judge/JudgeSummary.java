package breaktriage.judge;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JudgeSummary {

    public record Item(String caseId, JudgeResult result) {}

    private final List<Item> items;
    private final int failed;   // judge gave no valid scores, or the API was unreachable

    public JudgeSummary(List<Item> items, int failed) {
        this.items = items;
        this.failed = failed;
    }

    public int judged() {
        return items.size();
    }

    public int failed() {
        return failed;
    }

    public double average(String criterion) {
        return items.stream()
                .mapToInt(i -> i.result().scores().get(criterion).score())
                .average().orElse(0);
    }

    public List<Item> lowest(int n) {
        return items.stream()
                .sorted(Comparator.comparingInt((Item i) -> i.result().lowest())
                        .thenComparing(Item::caseId))
                .limit(n)
                .toList();
    }

    public void print() {
        System.out.printf("%nJudge (prompt %s): judged %d drafts, %d failed%n",
                JudgePromptBuilder.VERSION, judged(), failed());
        if (items.isEmpty()) {
            return;
        }
        StringBuilder avg = new StringBuilder();
        for (String criterion : DraftJudge.CRITERIA) {
            avg.append(String.format("%s=%.2f  ", criterion, average(criterion)));
        }
        System.out.println("  Average scores: " + avg.toString().strip());
        System.out.println("  Lowest-scoring drafts:");
        for (Item item : lowest(3)) {
            Map.Entry<String, JudgeScore> worst = item.result().scores().entrySet().stream()
                    .min(Comparator.comparingInt(e -> e.getValue().score()))
                    .orElseThrow();
            System.out.printf("    %s  %s=%d  %s%n", item.caseId(), worst.getKey(),
                    worst.getValue().score(), worst.getValue().reason());
        }
        printAll();
    }

    public void printAll() {
        System.out.println("  All judged drafts (accuracy clarity ask tone commitments):");
        items.stream().sorted(Comparator.comparing(Item::caseId)).forEach(item -> {
            String scores = DraftJudge.CRITERIA.stream()
                    .map(c -> String.valueOf(item.result().scores().get(c).score()))
                    .collect(Collectors.joining(" "));
            System.out.printf("    %s  %s%n", item.caseId(), scores);
            for (String c : DraftJudge.CRITERIA) {
                JudgeScore s = item.result().scores().get(c);
                if (s.score() <= 4) {
                    System.out.printf("        %s=%d: %s%n", c, s.score(), s.reason());
                }
            }
        });
    }
}