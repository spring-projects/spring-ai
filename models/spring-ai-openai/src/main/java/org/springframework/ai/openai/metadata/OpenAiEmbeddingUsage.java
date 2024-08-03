package org.springframework.ai.openai.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.Assert;


/**
 * @author hamburger
 * @since 2024/8/3
 */
public class OpenAiEmbeddingUsage implements Usage {

	public static OpenAiEmbeddingUsage from(OpenAiApi.Usage usage) {
		Assert.notNull(usage, "OpenAiEmbeddingsUsage must not be null");
		return new OpenAiEmbeddingUsage(usage);
	}


	private final OpenAiApi.Usage usage;


	public OpenAiEmbeddingUsage(OpenAiApi.Usage usage) {
		Assert.notNull(usage, "OpenAiEmbeddingsUsage must not be null");
		this.usage = usage;
	}


	protected OpenAiApi.Usage getUsage() {
		return this.usage;
	}


	@Override
	public Long getPromptTokens() {
		return (long) getUsage().promptTokens();
	}


	@Override
	public Long getGenerationTokens() {
		return 0L;
	}


	@Override
	public Long getTotalTokens() {
		return (long) getUsage().totalTokens();
	}


	@Override
	public String toString() {
		return getUsage().toString();
	}

}
