package breaktriage.eval;

public record RunSettings(
        String model,
        String draftModel,
        boolean classifierThinkingOff,
        boolean draftThinkingOff,
        String evalSet) {

    public String classifierThinking() { return classifierThinkingOff ? "off" : "default"; }
    public String draftThinking()      { return draftThinkingOff ? "off" : "default"; }
}