package breaktriage.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;

import java.time.Duration;
import java.util.stream.Collectors;

public final class ClaudeClient implements LlmClient {

     private final AnthropicClient client = AnthropicOkHttpClient.builder()
            .fromEnv()
            .maxRetries(4)
            .timeout(Duration.ofSeconds(30))
            .build();
            
    private final String model;

    public ClaudeClient(String model) { this.model = model; }

    @Override
    public LlmResponse complete(String system, String user) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(system)
                .addUserMessage(user)
                .build();

        long start = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            // The SDK has already retried with backoff. Anything else (bad key, bad request)
            // is a bug or a config error, so it is deliberately not caught here.
            throw new LlmUnavailableException("Claude API unavailable after retries: " + e.getMessage(), e);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());

        return new LlmResponse(text, message.usage().inputTokens(),
                message.usage().outputTokens(), ms);
    }
}