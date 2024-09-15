package org.springframework.ai.moonshot.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.util.Assert;

/**
 * @author Geng Rong
 */
public class MoonshotUsage implements Usage {

	private final MoonshotApi.Usage usage;

	public static MoonshotUsage from(MoonshotApi.Usage usage) {
		return new MoonshotUsage(usage);
	}

	protected MoonshotUsage(MoonshotApi.Usage usage) {
		Assert.notNull(usage, "Moonshot Usage must not be null");
		this.usage = usage;
	}

	protected MoonshotApi.Usage getUsage() {
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
