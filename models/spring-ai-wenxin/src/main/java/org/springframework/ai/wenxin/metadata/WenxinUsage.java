package org.springframework.ai.wenxin.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.util.Assert;

/**
 * @author lvchzh
 * @since 1.0.0
 */
public class WenxinUsage implements Usage {

	private final WenxinApi.Usage usage;

	protected WenxinUsage(WenxinApi.Usage usage) {
		Assert.notNull(usage, "Wenxin Usage must not be null");
		this.usage = usage;
	}

	public static WenxinUsage from(WenxinApi.Usage usage) {
		return new WenxinUsage(usage);
	}

	protected WenxinApi.Usage getUsage() {
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
