package breaktriage.llm;

public interface LlmClient {
    LlmResponse complete(String system, String user);
}