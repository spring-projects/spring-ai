/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.api.ChatCompletionRequestBuilder;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(OpenAiChatProperties.CONFIG_PREFIX)
public class OpenAiChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.chat";

	public static Float DEFAULT_TEMPERATURE = 0.7f;

	public static String DEFAULT_MODEL = "gpt-3.5-turbo";

	private final Metadata metadata = new Metadata();

	private ChatCompletionRequest options = ChatCompletionRequestBuilder.builder()
		.withModel(DEFAULT_MODEL)
		.withTemperature(DEFAULT_TEMPERATURE)
		.build();

	// private OpenAiOptions options = OpenAiOptionsBuilder.builder()
	// .withModel(DEFAULT_MODEL)
	// .withTemperature(DEFAULT_TEMPERATURE)
	// .build();

	// public OpenAiOptions getOptions() {
	// return options;
	// }

	public Metadata getMetadata() {
		return this.metadata;
	}

	public ChatCompletionRequest getOptions() {
		return options;
	}

	public void setOptions(ChatCompletionRequest options) {
		this.options = options;
	}

	public static class Metadata {

		private Boolean rateLimitMetricsEnabled;

		public boolean isRateLimitMetricsEnabled() {
			return Boolean.TRUE.equals(getRateLimitMetricsEnabled());
		}

		public Boolean getRateLimitMetricsEnabled() {
			return this.rateLimitMetricsEnabled;
		}

		public void setRateLimitMetricsEnabled(Boolean rateLimitMetricsEnabled) {
			this.rateLimitMetricsEnabled = rateLimitMetricsEnabled;
		}

	}

}
