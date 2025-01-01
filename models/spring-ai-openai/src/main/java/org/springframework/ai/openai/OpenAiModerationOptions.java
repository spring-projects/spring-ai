/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.openai.api.OpenAiModerationApi;

/**
 * OpenAI Moderation API options. OpenAiModerationOptions.java
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiModerationOptions implements ModerationOptions {

	/**
	 * The model to use for moderation generation.
	 */
	@JsonProperty("model")
	private String model = OpenAiModerationApi.DEFAULT_MODERATION_MODEL;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public static final class Builder {

		private final OpenAiModerationOptions options;

		private Builder() {
			this.options = new OpenAiModerationOptions();
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		/**
		 * @deprecated use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public OpenAiModerationOptions build() {
			return this.options;
		}

	}

}
