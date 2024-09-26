package org.springframework.ai.anthropic.api;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.CacheControl;

import java.util.function.Supplier;

public enum AnthropicCacheType {
	EPHEMERAL(() -> new CacheControl("ephemeral"));

	private Supplier<CacheControl> value;

	AnthropicCacheType(Supplier<CacheControl> value) {
		this.value = value;
	}

	public CacheControl cacheControl() {
		return this.value.get();
	}
}
