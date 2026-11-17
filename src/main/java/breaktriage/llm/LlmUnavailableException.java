package breaktriage.llm;

/** The model could not be reached after the client's own retries. Safe to escalate to a human. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}