package org.springframework.ai.ollama.metadata;

import java.util.Optional;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Ollama}
 *
 * @see Usage
 * @author Fu Cheng
 */
public class OllamaUsage implements Usage {

	protected static final String AI_USAGE_STRING = "{ promptTokens: %1$d, generationTokens: %2$d, totalTokens: %3$d }";

	public static OllamaUsage from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		return new OllamaUsage(response);
	}

	private final OllamaApi.ChatResponse response;

	public OllamaUsage(OllamaApi.ChatResponse response) {
		this.response = response;
	}

	@Override
	public Long getPromptTokens() {
		return Optional.ofNullable(response.promptEvalCount()).map(Integer::longValue).orElse(0L);
	}

	@Override
	public Long getGenerationTokens() {
		return Optional.ofNullable(response.evalCount()).map(Integer::longValue).orElse(0L);
	}

	@Override
	public String toString() {
		return AI_USAGE_STRING.formatted(getPromptTokens(), getGenerationTokens(), getTotalTokens());
	}

}
