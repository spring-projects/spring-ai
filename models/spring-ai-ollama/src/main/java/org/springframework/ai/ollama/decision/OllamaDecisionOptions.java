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

package org.springframework.ai.ollama.decision;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DefaultDecisionOptions;

/**
 * A {@link DecisionOptions} implementation for Ollama.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class OllamaDecisionOptions extends DefaultDecisionOptions {

	protected OllamaDecisionOptions(@Nullable String model) {
		super(model);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Builder mutate() {
		return builder().model(getModel());
	}

	@Override
	public String toString() {
		return "OllamaDecisionOptions{model='" + getModel() + "'}";
	}

	public static class Builder extends DefaultDecisionOptions.Builder<Builder> {

		@Override
		public OllamaDecisionOptions build() {
			return new OllamaDecisionOptions(this.model);
		}

	}

}
