package breaktriage.llm;

public record LlmResponse(String text, long inputTokens, long outputTokens, long latencyMs) {}