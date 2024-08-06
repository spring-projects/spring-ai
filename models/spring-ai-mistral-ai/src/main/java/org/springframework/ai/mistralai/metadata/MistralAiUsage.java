package org.springframework.ai.mistralai.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Mistral AI}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="https://docs.mistral.ai/api/">Chat Completion API</a>
 */
public class MistralAiUsage implements Usage {

	public static MistralAiUsage from(MistralAiApi.Usage usage) {
		return new MistralAiUsage(usage);
	}

	private final MistralAiApi.Usage usage;

	protected MistralAiUsage(MistralAiApi.Usage usage) {
		Assert.notNull(usage, "Mistral AI Usage must not be null");
		this.usage = usage;
	}

	protected MistralAiApi.Usage getUsage() {
		return this.usage;
	}

	@Override
	public Long getPromptTokens() {
		return getUsage().promptTokens().longValue();
	}

	@Override
	public Long getGenerationTokens() {
		return getUsage().completionTokens().longValue();
	}

	@Override
	public Long getTotalTokens() {
		return getUsage().totalTokens().longValue();
	}

	@Override
	public String toString() {
		return getUsage().toString();
	}

}
