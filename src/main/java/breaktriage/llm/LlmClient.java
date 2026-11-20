package breaktriage.llm;

@FunctionalInterface 
public interface LlmClient {
    LlmResponse complete(String system, String user);
}