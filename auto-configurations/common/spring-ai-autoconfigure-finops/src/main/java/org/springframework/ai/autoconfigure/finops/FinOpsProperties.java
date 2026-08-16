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

package org.springframework.ai.autoconfigure.finops;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Spring AI FinOps token-usage observability module.
 *
 * <p>
 * Numeric properties are validated eagerly in the compact constructor (rather than via
 * JSR-303 {@code @Validated}) so that misconfiguration is caught at startup without
 * requiring consumers to add a Jakarta Bean Validation provider (e.g. Hibernate
 * Validator) to their classpath purely for this module.
 *
 * <pre>
 * spring.ai.finops.enabled=true
 * spring.ai.finops.price-per-million-prompt-tokens=3.00
 * spring.ai.finops.price-per-million-completion-tokens=15.00
 * spring.ai.finops.budget-threshold-usd=10.00
 * </pre>
 */
@ConfigurationProperties(prefix = "spring.ai.finops")
public record FinOpsProperties(

		/** Enable or disable the FinOps metering advisor. */
		@DefaultValue("false") boolean enabled,

		/**
		 * Price in USD per 1,000,000 prompt (input) tokens. Set to 0 to disable cost
		 * estimation (default). Example: GPT-4o = 2.50, GPT-4o-mini = 0.15
		 */
		@DefaultValue("0.0") double pricePerMillionPromptTokens,

		/**
		 * Price in USD per 1,000,000 completion (output) tokens. Set to 0 to disable cost
		 * estimation (default). Example: GPT-4o = 10.00, GPT-4o-mini = 0.60
		 */
		@DefaultValue("0.0") double pricePerMillionCompletionTokens,

		/**
		 * Cumulative estimated USD cost at which the advisor will throw a
		 * TokenBudgetExceededException, acting as a "light switch" to stop further LLM
		 * usage. Set to 0 (default) to disable budget enforcement.
		 */
		@DefaultValue("0.0") double budgetThresholdUsd) {

	public FinOpsProperties {
		if (pricePerMillionPromptTokens < 0.0) {
			throw new IllegalArgumentException("spring.ai.finops.price-per-million-prompt-tokens must be >= 0, got: "
					+ pricePerMillionPromptTokens);
		}
		if (pricePerMillionCompletionTokens < 0.0) {
			throw new IllegalArgumentException(
					"spring.ai.finops.price-per-million-completion-tokens must be >= 0, got: "
							+ pricePerMillionCompletionTokens);
		}
		if (budgetThresholdUsd < 0.0) {
			throw new IllegalArgumentException(
					"spring.ai.finops.budget-threshold-usd must be >= 0, got: " + budgetThresholdUsd);
		}
	}

}
