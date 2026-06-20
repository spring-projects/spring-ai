/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mistralai.moderation;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.moderation.ModerationOptions;

/**
 * @author Ricken Bazolo
 * @author Sebastien Deleuze
 */
public class MistralAiModerationOptions implements ModerationOptions {

	private static final String DEFAULT_MODEL = MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue();

	/**
	 * The model to use for moderation generation.
	 */
	private final String model;

	protected MistralAiModerationOptions(@Nullable String model) {
		this.model = (model != null ? model : DEFAULT_MODEL);
	}

	public static MistralAiModerationOptions.Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public static final class Builder {

		private @Nullable String model;

		private Builder() {
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public MistralAiModerationOptions build() {
			return new MistralAiModerationOptions(this.model);
		}

	}

}
