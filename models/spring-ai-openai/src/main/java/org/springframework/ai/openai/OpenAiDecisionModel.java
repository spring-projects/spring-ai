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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.observation.DecisionModelObservationContext;
import org.springframework.ai.decision.observation.DecisionModelObservationConvention;
import org.springframework.ai.decision.observation.DecisionModelObservationDocumentation;
import org.springframework.ai.decision.observation.DefaultDecisionModelObservationConvention;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link DecisionModel} backed by the OpenAI Decisions API, through the official OpenAI
 * Java SDK.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 * @see <a href=
 * "https://developers.openai.com/api/reference/resources/decisions/methods/create">OpenAI
 * Create Decision</a>
 */
public final class OpenAiDecisionModel implements DecisionModel {

	private static final Log logger = LogFactory.getLog(OpenAiDecisionModel.class);

	private static final DecisionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultDecisionModelObservationConvention();

	private final OpenAIClient openAiClient;

	private final OpenAiDecisionOptions options;

	private final ObservationRegistry observationRegistry;

	private final DecisionModelObservationConvention observationConvention;

	private OpenAiDecisionModel(Builder builder) {
		OpenAiDecisionOptions options = Objects.requireNonNullElseGet(builder.options,
				() -> OpenAiDecisionOptions.builder().build());
		this.options = options.getModel() != null ? options
				: options.mutate().model(OpenAiDecisionOptions.DEFAULT_DECISION_MODEL).build();
		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
		this.observationConvention = Objects.requireNonNullElse(builder.observationConvention,
				DEFAULT_OBSERVATION_CONVENTION);
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						Objects.requireNonNullElse(this.options.getTimeout(), AbstractOpenAiOptions.DEFAULT_TIMEOUT),
						this.options.getMaxRetries(), this.options.getProxy(), this.options.getCustomHeaders(),
						this.observationRegistry, null, builder.httpClientCustomizers));
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder mutate() {
		return new Builder(this);
	}

	@Override
	public DecisionResponse call(DecisionPrompt prompt) {
		Assert.notNull(prompt, "prompt cannot be null");

		OpenAiDecisionOptions.Builder optionsBuilder = this.options.mutate();
		if (prompt.getOptions() != null) {
			optionsBuilder.combineWith(prompt.getOptions().mutate());
		}
		OpenAiDecisionOptions options = optionsBuilder.build();

		DecisionCreateParams params = createParams(prompt, options);
		RequestOptions requestOptions = buildRequestOptions(options);

		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiDecisionModel call " + options.getModel() + " with the following params: " + params);
		}

		// The observation sees the merged options, so the model name is always recorded.
		DecisionPrompt observedPrompt = prompt.mutate().options(options).build();
		var observationContext = DecisionModelObservationContext.builder()
			.decisionPrompt(observedPrompt)
			.provider(AiProvider.OPENAI.value())
			.build();

		return Objects.requireNonNull(
				DecisionModelObservationDocumentation.DECISION_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						Decision decision = this.openAiClient.decisions().create(params, requestOptions);
						DecisionResponse response = OpenAiDecisionMapper.response(prompt, decision);
						observationContext.setResponse(response);
						return response;
					}));
	}

	private static DecisionCreateParams createParams(DecisionPrompt prompt, OpenAiDecisionOptions options) {
		String model = options.getDeploymentName() != null ? options.getDeploymentName() : options.getModel();
		Assert.notNull(model, "Model must not be null");

		DecisionCreateParams.Builder builder = DecisionCreateParams.builder()
			.model(model)
			.input(OpenAiDecisionMapper.input(prompt.getInstructions()));
		prompt.getQuestions()
			.forEach((name, question) -> builder.addQuestion(OpenAiDecisionMapper.question(name, question)));
		if (options.getSafetyIdentifier() != null) {
			builder.safetyIdentifier(options.getSafetyIdentifier());
		}
		if (!CollectionUtils.isEmpty(options.getCustomHeaders())) {
			options.getCustomHeaders().forEach(builder::putAdditionalHeader);
		}
		return builder.build();
	}

	private static RequestOptions buildRequestOptions(OpenAiDecisionOptions options) {
		RequestOptions.Builder requestOptionsBuilder = RequestOptions.builder();
		if (options.getTimeout() != null) {
			requestOptionsBuilder.timeout(options.getTimeout());
		}
		return requestOptionsBuilder.build();
	}

	public OpenAiDecisionOptions getOptions() {
		return this.options;
	}

	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiDecisionOptions options;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable DecisionModelObservationConvention observationConvention;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		private Builder(OpenAiDecisionModel model) {
			this.openAiClient = model.openAiClient;
			this.options = model.options;
			this.observationRegistry = model.observationRegistry;
			this.observationConvention = model.observationConvention;
		}

		public Builder openAiClient(@Nullable OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder options(@Nullable OpenAiDecisionOptions options) {
			this.options = options;
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

		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		public OpenAiDecisionModel build() {
			return new OpenAiDecisionModel(this);
		}

	}

}
