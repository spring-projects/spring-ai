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
package org.springframework.ai.chat.prompt;

public class ChatOptionsBuilder {

	private Float temperature;

	private Float topP;

	private Integer topK;

	private ChatOptionsBuilder() {
	}

	/**
	 * Creates a new {@link ChatOptions} instance.
	 * @return A new instance of ChatOptionsBuilder.
	 */
	public static ChatOptionsBuilder builder() {
		return new ChatOptionsBuilder();
	}

	/**
	 * Initializes a new {@link ChatOptionsBuilder} with settings from an existing
	 * {@link ChatOptions} object.
	 * @param options The ChatOptions object whose settings are to be used.
	 * @return A ChatOptionsBuilder instance initialized with the provided ChatOptions
	 * settings.
	 */
	public static ChatOptionsBuilder builder(final ChatOptions options) {
		return builder().withTemperature(options.getTemperature())
			.withTopK(options.getTopK())
			.withTopP(options.getTopP());
	}

	public ChatOptionsBuilder withTemperature(final Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public ChatOptionsBuilder withTopP(final Float topP) {
		this.topP = topP;
		return this;
	}

	public ChatOptionsBuilder withTopK(final Integer topK) {
		this.topK = topK;
		return this;
	}

	public ChatOptions build() {
		return new ChatOptionsImpl(this.temperature, this.topP, this.topK);
	}

	/**
	 * Created only by ChatOptionsBuilder for controlled setup. Hidden implementation,
	 * accessed via ChatOptions interface. Promotes modularity and easy use.
	 */
	private class ChatOptionsImpl implements ChatOptions {

		private final Float temperature;

		private final Float topP;

		private final Integer topK;

		ChatOptionsImpl(final Float temperature, final Float topP, final Integer topK) {
			this.temperature = temperature;
			this.topP = topP;
			this.topK = topK;
		}

		@Override
		public Float getTemperature() {
			return temperature;
		}

		@Override
		public Float getTopP() {
			return topP;
		}

		@Override
		public Integer getTopK() {
			return topK;
		}

	}

}