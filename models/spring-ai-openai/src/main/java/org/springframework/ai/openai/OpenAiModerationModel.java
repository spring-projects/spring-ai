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

import com.openai.client.OpenAIClient;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.util.Assert;

/**
 * OpenAI SDK Moderation Model implementation.
 * <p>
 * This model provides content moderation capabilities using the OpenAI Moderation API
 * through the official OpenAI Java SDK.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 */
public final class OpenAiModerationModel implements ModerationModel {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiModerationModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiModerationOptions defaultOptions;

	private OpenAiModerationModel(Builder builder) {
		if (builder.options == null) {
			this.defaultOptions = OpenAiModerationOptions.builder()
				.model(OpenAiModerationOptions.DEFAULT_MODERATION_MODEL)
				.build();
		}
		else {
			this.defaultOptions = builder.options;
		}

		this.openAiClient = java.util.Objects.requireNonNullElseGet(builder.openAiClient,
				() -> org.springframework.ai.openai.setup.OpenAiSetup.setupSyncClient(this.defaultOptions.getBaseUrl(),
						this.defaultOptions.getApiKey(), this.defaultOptions.getCredential(),
						this.defaultOptions.getMicrosoftDeploymentName(),
						this.defaultOptions.getMicrosoftFoundryServiceVersion(),
						this.defaultOptions.getOrganizationId(), this.defaultOptions.isMicrosoftFoundry(),
						this.defaultOptions.isGitHubModels(), this.defaultOptions.getModel(),
						this.defaultOptions.getTimeout(), this.defaultOptions.getMaxRetries(),
						this.defaultOptions.getProxy(), this.defaultOptions.getCustomHeaders()));
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder mutate() {
		return new Builder(this);
	}

	@Override
	public ModerationResponse call(ModerationPrompt moderationPrompt) {
		String text = moderationPrompt.getInstructions().getText();

		OpenAiModerationOptions options = merge(moderationPrompt.getOptions(), this.defaultOptions);

		ModerationCreateParams.Builder builder = ModerationCreateParams.builder()
			.input(ModerationCreateParams.Input.ofString(text));

		String model;
		if (options.getDeploymentName() != null) {
			model = options.getDeploymentName();
		}
		else {
			model = options.getModel();
		}
		Assert.notNull(model, "Model must not be null");
		builder.model(com.openai.models.moderations.ModerationModel.of(model));

		ModerationCreateParams params = builder.build();

		ModerationCreateResponse response = this.openAiClient.moderations().create(params);

		return convertResponse(response);
	}

	private ModerationResponse convertResponse(ModerationCreateResponse response) {
		if (response == null) {
			logger.warn("No moderation response returned");
			return new ModerationResponse(null);
		}

		List<ModerationResult> moderationResults = new ArrayList<>();

		for (com.openai.models.moderations.Moderation result : response.results()) {
			Categories categories = Categories.builder()
				.sexual(result.categories().sexual())
				.hate(result.categories().hate())
				.harassment(result.categories().harassment())
				.selfHarm(result.categories().selfHarm())
				.sexualMinors(result.categories().sexualMinors())
				.hateThreatening(result.categories().hateThreatening())
				.violenceGraphic(result.categories().violenceGraphic())
				.selfHarmIntent(result.categories().selfHarmIntent())
				.selfHarmInstructions(result.categories().selfHarmInstructions())
				.harassmentThreatening(result.categories().harassmentThreatening())
				.violence(result.categories().violence())
				.build();

			CategoryScores categoryScores = CategoryScores.builder()
				.hate(result.categoryScores().hate())
				.hateThreatening(result.categoryScores().hateThreatening())
				.harassment(result.categoryScores().harassment())
				.harassmentThreatening(result.categoryScores().harassmentThreatening())
				.selfHarm(result.categoryScores().selfHarm())
				.selfHarmIntent(result.categoryScores().selfHarmIntent())
				.selfHarmInstructions(result.categoryScores().selfHarmInstructions())
				.sexual(result.categoryScores().sexual())
				.sexualMinors(result.categoryScores().sexualMinors())
				.violence(result.categoryScores().violence())
				.violenceGraphic(result.categoryScores().violenceGraphic())
				.build();

			ModerationResult moderationResult = ModerationResult.builder()
				.categories(categories)
				.categoryScores(categoryScores)
				.flagged(result.flagged())
				.build();

			moderationResults.add(moderationResult);
		}

		Moderation moderation = Moderation.builder()
			.id(response.id())
			.model(response.model())
			.results(moderationResults)
			.build();

		return new ModerationResponse(new Generation(moderation));
	}

	private static OpenAiModerationOptions merge(@Nullable ModerationOptions source, OpenAiModerationOptions target) {
		return OpenAiModerationOptions.builder().from(target).merge(source).build();
	}

	public OpenAiModerationOptions getOptions() {
		return this.defaultOptions;
	}

	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiModerationOptions options;

		private Builder() {
		}

		private Builder(OpenAiModerationModel model) {
			this.openAiClient = model.openAiClient;
			this.options = model.defaultOptions;
		}

		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder options(OpenAiModerationOptions options) {
			this.options = options;
			return this;
		}

		public OpenAiModerationModel build() {
			return new OpenAiModerationModel(this);
		}

	}

}
