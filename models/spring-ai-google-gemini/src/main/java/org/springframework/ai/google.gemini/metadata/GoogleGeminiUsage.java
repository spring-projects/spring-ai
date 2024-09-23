/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.google.gemini.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi;
import org.springframework.util.Assert;

/**
 * @author Geng Rong
 */
public class GoogleGeminiUsage implements Usage {

	public static GoogleGeminiUsage from(GoogleGeminiApi.Usage usage) {
		return new GoogleGeminiUsage(usage);
	}

	private final GoogleGeminiApi.Usage usage;

	protected GoogleGeminiUsage(GoogleGeminiApi.Usage usage) {
		Assert.notNull(usage, "Google Gemini Usage must not be null");
		this.usage = usage;
	}

	protected GoogleGeminiApi.Usage getUsage() {
		return this.usage;
	}

	@Override
	public Integer getPromptTokens() {
		var tokenCount = getUsage().promptTokenCount();
		return tokenCount == null ? 0 : tokenCount;
	}

	@Override
	public Integer getCompletionTokens() {
		var candidatesTokenCount = getUsage().candidatesTokenCount();
		var toolUsePromptTokenCount = getUsage().toolUsePromptTokenCount();
		var tokenCount = candidatesTokenCount != null ? candidatesTokenCount : 0;
		tokenCount += toolUsePromptTokenCount != null ? toolUsePromptTokenCount : 0;
		return tokenCount;
	}

	public Integer getThoughtsTokens() {
		var thoughtsTokenCount = getUsage().thoughtsTokenCount();
		var tokenCount = thoughtsTokenCount != null ? thoughtsTokenCount : 0;
		return tokenCount;
	}

	public Integer getCacheHitTokens() {
		var tokenCount = getUsage().cachedContentTokenCount();
		return tokenCount == null ? 0 : tokenCount;
	}

	public Integer getCacheMissTokens() {
		var t1 = getPromptTokens();
		if (t1 == null) {
			return null;
		}
		var t2 = getCacheHitTokens();
		return t2 != null ? t1 - t2 : null;
	}

	@Override
	public Integer getTotalTokens() {
		var tokenCount = getUsage().totalTokenCount();
		return tokenCount == null ? 0 : tokenCount;
	}

	@Override
	public Object getNativeUsage() {
		return getUsage();
	}

	@Override
	public String toString() {
		return getUsage().toString();
	}

}
