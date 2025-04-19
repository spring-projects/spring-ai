/*
 * Copyright 2025-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import static org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationRequest;
import static org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationResponse;
import static org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationResult;

/**
 * @author Ricken Bazolo
 */
public class MistralAiModerationModel implements ModerationModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final MistralAiModerationApi mistralAiModerationApi;

	private final RetryTemplate retryTemplate;

	private final MistralAiModerationOptions defaultOptions;

	public MistralAiModerationModel(MistralAiModerationApi mistralAiModerationApi) {
		this(mistralAiModerationApi, RetryUtils.DEFAULT_RETRY_TEMPLATE,
				MistralAiModerationOptions.builder()
					.model(MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue())
					.build());
	}

	public MistralAiModerationModel(MistralAiModerationApi mistralAiModerationApi, MistralAiModerationOptions options) {
		this(mistralAiModerationApi, RetryUtils.DEFAULT_RETRY_TEMPLATE, options);
	}

	public MistralAiModerationModel(MistralAiModerationApi mistralAiModerationApi, RetryTemplate retryTemplate,
			MistralAiModerationOptions options) {
		Assert.notNull(mistralAiModerationApi, "mistralAiModerationApi must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(options, "options must not be null");
		this.mistralAiModerationApi = mistralAiModerationApi;
		this.retryTemplate = retryTemplate;
		this.defaultOptions = options;
	}

	@Override
	public ModerationResponse call(ModerationPrompt moderationPrompt) {
		return this.retryTemplate.execute(ctx -> {

			var instructions = moderationPrompt.getInstructions().getText();

			var moderationRequest = new MistralAiModerationRequest(instructions);

			if (this.defaultOptions != null) {
				moderationRequest = ModelOptionsUtils.merge(this.defaultOptions, moderationRequest,
						MistralAiModerationRequest.class);
			}
			else {
				// moderationPrompt.getOptions() never null but model can be empty, cause
				// by ModerationPrompt constructor
				moderationRequest = ModelOptionsUtils.merge(toMistralAiModerationOptions(moderationPrompt.getOptions()),
						moderationRequest, MistralAiModerationRequest.class);
			}

			var moderationResponseEntity = this.mistralAiModerationApi.moderate(moderationRequest);

			return convertResponse(moderationResponseEntity, moderationRequest);
		});
	}

	private ModerationResponse convertResponse(ResponseEntity<MistralAiModerationResponse> moderationResponseEntity,
			MistralAiModerationRequest openAiModerationRequest) {
		var moderationApiResponse = moderationResponseEntity.getBody();
		if (moderationApiResponse == null) {
			logger.warn("No moderation response returned for request: {}", openAiModerationRequest);
			return new ModerationResponse(new Generation());
		}

		List<ModerationResult> moderationResults = new ArrayList<>();
		if (moderationApiResponse.results() != null) {

			for (MistralAiModerationResult result : moderationApiResponse.results()) {
				Categories categories = null;
				CategoryScores categoryScores = null;
				if (result.categories() != null) {
					categories = Categories.builder()
						.sexual(result.categories().sexual())
						.pii(result.categories().pii())
						.law(result.categories().law())
						.financial(result.categories().financial())
						.health(result.categories().health())
						.dangerousAndCriminalContent(result.categories().dangerousAndCriminalContent())
						.violence(result.categories().violenceAndThreats())
						.hate(result.categories().hateAndDiscrimination())
						.selfHarm(result.categories().selfHarm())
						.build();
				}
				if (result.categoryScores() != null) {
					categoryScores = CategoryScores.builder()
						.sexual(result.categoryScores().sexual())
						.pii(result.categoryScores().pii())
						.law(result.categoryScores().law())
						.financial(result.categoryScores().financial())
						.health(result.categoryScores().health())
						.dangerousAndCriminalContent(result.categoryScores().dangerousAndCriminalContent())
						.violence(result.categoryScores().violenceAndThreats())
						.hate(result.categoryScores().hateAndDiscrimination())
						.selfHarm(result.categoryScores().selfHarm())
						.build();
				}
				var moderationResult = ModerationResult.builder()
					.categories(categories)
					.categoryScores(categoryScores)
					.flagged(result.flagged())
					.build();
				moderationResults.add(moderationResult);
			}

		}

		var moderation = Moderation.builder()
			.id(moderationApiResponse.id())
			.model(moderationApiResponse.model())
			.results(moderationResults)
			.build();

		return new ModerationResponse(new Generation(moderation));
	}

	private MistralAiModerationOptions toMistralAiModerationOptions(ModerationOptions runtimeModerationOptions) {
		var mistralAiModerationOptionsBuilder = MistralAiModerationOptions.builder();
		if (runtimeModerationOptions != null && runtimeModerationOptions.getModel() != null) {
			mistralAiModerationOptionsBuilder.model(runtimeModerationOptions.getModel());
		}
		return mistralAiModerationOptionsBuilder.build();
	}

}
