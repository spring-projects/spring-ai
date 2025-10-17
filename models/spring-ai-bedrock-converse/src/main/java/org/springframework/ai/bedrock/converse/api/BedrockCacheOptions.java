/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.bedrock.converse.api;

/**
 * AWS Bedrock cache options for configuring prompt caching behavior.
 *
 * <p>
 * Prompt caching allows you to reduce latency and costs by reusing previously processed
 * prompt content. Cached content has a fixed 5-minute Time To Live (TTL) that resets with
 * each cache hit.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
 *     .strategy(BedrockCacheStrategy.SYSTEM_ONLY)
 *     .build();
 *
 * ChatResponse response = chatModel.call(new Prompt(
 *     List.of(new SystemMessage(largeSystemPrompt), new UserMessage("Question")),
 *     BedrockChatOptions.builder()
 *         .cacheOptions(cacheOptions)
 *         .build()
 * ));
 * }</pre>
 *
 * @author Soby Chacko
 * @since 1.1.0
 * @see BedrockCacheStrategy
 * @see <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock
 * Prompt Caching</a>
 */
public class BedrockCacheOptions {

	private BedrockCacheStrategy strategy = BedrockCacheStrategy.NONE;

	/**
	 * Creates a new builder for constructing BedrockCacheOptions.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Gets the caching strategy.
	 * @return the configured BedrockCacheStrategy
	 */
	public BedrockCacheStrategy getStrategy() {
		return this.strategy;
	}

	/**
	 * Sets the caching strategy.
	 * @param strategy the BedrockCacheStrategy to use
	 */
	public void setStrategy(BedrockCacheStrategy strategy) {
		this.strategy = strategy;
	}

	@Override
	public String toString() {
		return "BedrockCacheOptions{" + "strategy=" + this.strategy + '}';
	}

	/**
	 * Builder for constructing BedrockCacheOptions instances.
	 */
	public static class Builder {

		private final BedrockCacheOptions options = new BedrockCacheOptions();

		/**
		 * Sets the caching strategy.
		 * @param strategy the BedrockCacheStrategy to use
		 * @return this Builder instance
		 */
		public Builder strategy(BedrockCacheStrategy strategy) {
			this.options.setStrategy(strategy);
			return this;
		}

		/**
		 * Builds the BedrockCacheOptions instance.
		 * @return the configured BedrockCacheOptions
		 */
		public BedrockCacheOptions build() {
			return this.options;
		}

	}

}
