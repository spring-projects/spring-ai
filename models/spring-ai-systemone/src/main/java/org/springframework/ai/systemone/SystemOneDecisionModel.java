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

package org.springframework.ai.systemone;

import java.util.Objects;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.observation.DecisionModelObservationContext;
import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.decision.observation.DecisionModelObservationDocumentation;
import org.springframework.ai.decision.observation.DefaultDecisionModelObservationConvention;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;

/**
 * A {@link DecisionModel} backed by a System One server through the TypeSafe Java SDK:
 * TypeSafe Jev, Laya or Ollama, since all three serve the same {@code /v1/systemone}
 * protocol. A {@code YesNoQuestion} is asked as a {@code noul}, a {@code ChoiceQuestion}
 * as a {@code choice} and a {@code ScoreQuestion} as a {@code score}, and the answers
 * come back with the probabilities and confidence the server reports.
 *
 * <p>
 * The connection belongs to the {@link TypeSafeClient}. The model takes the model name,
 * defaulting to the client's default model, and the provider name recorded in
 * observations, which is {@code systemone} unless the client points at a particular
 * server.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SystemOneDecisionModel implements DecisionModel {

	/**
	 * The provider name recorded in observations unless another one is configured.
	 */
	public static final String DEFAULT_PROVIDER = AiProvider.SYSTEMONE.value();

	private static final Log logger = LogFactory.getLog(SystemOneDecisionModel.class);

	private static final DecisionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultDecisionModelObservationConvention();

	private final TypeSafeClient typeSafeClient;

	private final SystemOneDecisionOptions options;

	private final String provider;

	private final ObservationRegistry observationRegistry;

	private final DecisionModelObservationConvention observationConvention;

	private SystemOneDecisionModel(TypeSafeClient typeSafeClient, SystemOneDecisionOptions options, String provider,
			ObservationRegistry observationRegistry, DecisionModelObservationConvention observationConvention) {
		Assert.hasText(options.getModel(), "options must name a model");
		this.typeSafeClient = typeSafeClient;
		this.options = options;
		this.provider = provider;
		this.observationRegistry = observationRegistry;
		this.observationConvention = observationConvention;
	}

	public static Builder builder(TypeSafeClient typeSafeClient) {
		return new Builder(typeSafeClient);
	}

	@Override
	public DecisionResponse call(DecisionPrompt prompt) {
		Assert.notNull(prompt, "prompt cannot be null");

		SystemOneDecisionOptions.Builder optionsBuilder = this.options.mutate();
		if (prompt.getOptions() != null) {
			optionsBuilder.combineWith(prompt.getOptions().mutate());
		}
		SystemOneDecisionOptions options = optionsBuilder.build();
		String model = options.getModel();
		Assert.hasText(model, "model cannot be null or empty");

		SystemOneRequest request = SystemOneRequest.builder()
			.state(SystemOneDecisionMapper.state(prompt.getInstructions()))
			.model(model)
			.questions(SystemOneDecisionMapper.questions(prompt))
			.build();

		if (logger.isTraceEnabled()) {
			logger.trace("SystemOneDecisionModel call " + model + " with the following request: " + request);
		}

		// The observation sees the merged options, so the model name is always recorded.
		DecisionPrompt observedPrompt = prompt.mutate().options(options).build();
		var observationContext = DecisionModelObservationContext.builder()
			.decisionPrompt(observedPrompt)
			.provider(this.provider)
			.build();

		return Objects.requireNonNull(
				DecisionModelObservationDocumentation.DECISION_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						SystemOneResponse response = this.typeSafeClient.systemOne(request);
						DecisionResponse decisionResponse = SystemOneDecisionMapper.response(prompt, response);
						observationContext.setResponse(decisionResponse);
						return decisionResponse;
					}));
	}

	/**
	 * The default options, with the model name applied.
	 */
	public SystemOneDecisionOptions getOptions() {
		return this.options;
	}

	/**
	 * The provider name recorded in observations.
	 */
	public String getProvider() {
		return this.provider;
	}

	public Builder mutate() {
		return new Builder(this);
	}

	public static final class Builder {

		private final TypeSafeClient typeSafeClient;

		private @Nullable SystemOneDecisionOptions options;

		private String provider = DEFAULT_PROVIDER;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable DecisionModelObservationConvention observationConvention;

		private Builder(TypeSafeClient typeSafeClient) {
			Assert.notNull(typeSafeClient, "typeSafeClient cannot be null");
			this.typeSafeClient = typeSafeClient;
		}

		private Builder(SystemOneDecisionModel model) {
			this.typeSafeClient = model.typeSafeClient;
			this.options = model.options;
			this.provider = model.provider;
			this.observationRegistry = model.observationRegistry;
			this.observationConvention = model.observationConvention;
		}

		/**
		 * The default options. When they name no model, the client's default model is
		 * used.
		 */
		public Builder options(@Nullable SystemOneDecisionOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * The provider name recorded in observations, such as {@code ollama} when the
		 * client points at an Ollama server. Defaults to {@link #DEFAULT_PROVIDER}.
		 */
		public Builder provider(String provider) {
			Assert.hasText(provider, "provider cannot be null or empty");
			this.provider = provider;
			return this;
		}

		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder observationConvention(@Nullable DecisionModelObservationConvention observationConvention) {
			this.observationConvention = observationConvention;
			return this;
		}

		public SystemOneDecisionModel build() {
			SystemOneDecisionOptions options = Objects.requireNonNullElseGet(this.options,
					() -> SystemOneDecisionOptions.builder().build());
			if (options.getModel() == null) {
				options = options.mutate().model(this.typeSafeClient.defaultModel()).build();
			}
			return new SystemOneDecisionModel(this.typeSafeClient, options, this.provider,
					Objects.requireNonNullElse(this.observationRegistry, ObservationRegistry.NOOP),
					Objects.requireNonNullElse(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION));
		}

	}

}
