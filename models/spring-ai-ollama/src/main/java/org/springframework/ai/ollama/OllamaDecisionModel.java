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

package org.springframework.ai.ollama;

import java.util.Objects;

import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.common.OllamaApiConstants;
import org.springframework.ai.ollama.decision.OllamaDecisionOptions;
import org.springframework.ai.ollama.decision.SystemOneDecisionMapper;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link DecisionModel} implementation for Ollama.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class OllamaDecisionModel implements DecisionModel {

	public static final String DEFAULT_MODEL = OllamaModel.D1_OMNI_600M.id();

	private static final DecisionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultDecisionModelObservationConvention();

	private final DecisionModelObservationConvention observationConvention;

	private final ObservationRegistry observationRegistry;

	private final OllamaDecisionOptions decisionOptions;

	private final TypeSafeClient systemOneClient;

	OllamaDecisionModel(DecisionModelObservationConvention observationConvention,
			ModelManagementOptions modelManagementOptions, OllamaDecisionOptions decisionOptions,
			ObservationRegistry observationRegistry, OllamaApi ollamaApi, TypeSafeClient systemOneClient) {
		Assert.hasText(decisionOptions.getModel(), "options must name a model");
		this.decisionOptions = decisionOptions;
		this.observationConvention = observationConvention;
		this.observationRegistry = observationRegistry;
		this.systemOneClient = systemOneClient;
		initializeModel(ollamaApi, decisionOptions.getModel(), modelManagementOptions);
	}

	@Override
	public DecisionResponse call(DecisionPrompt prompt) {
		Assert.notNull(prompt, "prompt cannot be null");

		OllamaDecisionOptions.Builder requestDecisionOptionsBuilder = this.decisionOptions.mutate();
		if (prompt.getOptions() != null) {
			requestDecisionOptionsBuilder.combineWith(prompt.getOptions().mutate());
		}
		OllamaDecisionOptions requestDecisionOptions = requestDecisionOptionsBuilder.build();
		String model = requestDecisionOptions.getModel();
		Assert.hasText(model, "model cannot be null or empty");

		SystemOneRequest systemOneRequest = SystemOneRequest.builder()
			.state(SystemOneDecisionMapper.state(prompt.getInstructions()))
			.model(model)
			.questions(SystemOneDecisionMapper.questions(prompt))
			.build();

		DecisionPrompt requestDecisionPrompt = prompt.mutate().options(requestDecisionOptions).build();

		DecisionModelObservationContext observationContext = DecisionModelObservationContext.builder()
			.decisionPrompt(requestDecisionPrompt)
			.provider(OllamaApiConstants.PROVIDER_NAME)
			.build();

		DecisionResponse ollamaDecisionResponse = DecisionModelObservationDocumentation.DECISION_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				SystemOneResponse systemOneResponse = this.systemOneClient.systemOne(systemOneRequest);
				DecisionResponse decisionResponse = SystemOneDecisionMapper.response(prompt, systemOneResponse);
				observationContext.setResponse(decisionResponse);
				return decisionResponse;
			});

		return Objects.requireNonNull(ollamaDecisionResponse, "a call to Ollama cannot result in a null response");
	}

	public OllamaDecisionOptions getOptions() {
		return this.decisionOptions;
	}

	private void initializeModel(OllamaApi ollamaApi, String model, ModelManagementOptions modelManagementOptions) {
		if (!PullModelStrategy.NEVER.equals(modelManagementOptions.pullModelStrategy())) {
			var modelManager = new OllamaModelManager(ollamaApi, modelManagementOptions);
			modelManager.pullModel(model, modelManagementOptions.pullModelStrategy());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable DecisionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

		private @Nullable ModelManagementOptions modelManagementOptions = ModelManagementOptions.defaults();

		private @Nullable OllamaDecisionOptions decisionOptions = OllamaDecisionOptions.builder().build();

		private @Nullable ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private @Nullable OllamaApi ollamaApi;

		private @Nullable TypeSafeClient systemOneClient;

		private Builder() {
		}

		public Builder observationConvention(DecisionModelObservationConvention observationConvention) {
			this.observationConvention = observationConvention;
			return this;
		}

		public Builder modelManagementOptions(ModelManagementOptions modelManagementOptions) {
			this.modelManagementOptions = modelManagementOptions;
			return this;
		}

		public Builder decisionOptions(OllamaDecisionOptions decisionOptions) {
			this.decisionOptions = decisionOptions;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder ollamaApi(OllamaApi ollamaApi) {
			this.ollamaApi = ollamaApi;
			return this;
		}

		public Builder systemOneClient(TypeSafeClient systemOneClient) {
			this.systemOneClient = systemOneClient;
			return this;
		}

		public OllamaDecisionModel build() {
			Assert.notNull(this.observationConvention, "observationConvention cannot be null");
			Assert.notNull(this.modelManagementOptions, "modelManagementOptions cannot be null");
			Assert.notNull(this.decisionOptions, "decisionOptions cannot be null");
			Assert.notNull(this.observationRegistry, "observationRegistry cannot be null");
			Assert.notNull(this.ollamaApi, "ollamaApi cannot be null");
			Assert.notNull(this.systemOneClient, "systemOneClient cannot be null");

			OllamaDecisionOptions options = this.decisionOptions;
			if (!StringUtils.hasText(options.getModel())) {
				options = options.mutate().model(DEFAULT_MODEL).build();
			}

			return new OllamaDecisionModel(this.observationConvention, this.modelManagementOptions, options,
					this.observationRegistry, this.ollamaApi, this.systemOneClient);
		}

	}

}
