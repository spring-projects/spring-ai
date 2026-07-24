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

import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationRequest;
import org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationResponse;
import org.springframework.ai.mistralai.api.MistralAiModerationApi.MistralAiModerationResult;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * @author Ricken Bazolo
 * @author Jason Smith
 * @author Sebastien Deleuze
 * @author Nicolas Krier
 */
public class MistralAiModerationModel implements ModerationModel {

	private final Log logger = LogFactory.getLog(getClass());

	private final MistralAiModerationApi mistralAiModerationApi;

	private final RetryTemplate retryTemplate;

	private final MistralAiModerationOptions options;

	public MistralAiModerationModel(MistralAiModerationApi mistralAiModerationApi, RetryTemplate retryTemplate,
			MistralAiModerationOptions options) {
		Assert.notNull(mistralAiModerationApi, "mistralAiModerationApi must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(options, "options must not be null");
		this.mistralAiModerationApi = mistralAiModerationApi;
		this.retryTemplate = retryTemplate;
		this.options = options;
	}

	@Override
	public ModerationResponse call(ModerationPrompt moderationPrompt) {
		return RetryUtils.execute(this.retryTemplate, () -> {
			var instructions = moderationPrompt.getInstructions().getText();
			var model = ModelOptionsUtils.mergeOption(moderationPrompt.getOptions().getModel(),
					this.options.getModel());
			var moderationRequest = new MistralAiModerationRequest(instructions, model);
			var moderationResponseEntity = this.mistralAiModerationApi.moderate(moderationRequest);

			return convertResponse(moderationResponseEntity, moderationRequest);
		});
	}

	private ModerationResponse convertResponse(ResponseEntity<MistralAiModerationResponse> moderationResponseEntity,
			MistralAiModerationRequest mistralAiModerationRequest) {
		var moderationApiResponse = moderationResponseEntity.getBody();

		if (moderationApiResponse == null) {
			if (logger.isWarnEnabled()) {
				logger.warn("No moderation response returned for request: " + mistralAiModerationRequest);
			}
			return new ModerationResponse(null);
		}

		var moderationResults = Stream.of(moderationApiResponse.results())
			.map(MistralAiModerationModel::convertModerationResult)
			.toList();

		var moderation = Moderation.builder()
			.id(moderationApiResponse.id())
			.model(moderationApiResponse.model())
			.results(moderationResults)
			.build();

		return new ModerationResponse(new Generation(moderation));
	}

	private static ModerationResult convertModerationResult(MistralAiModerationResult mistralAiModerationResult) {
		var sourceCategories = mistralAiModerationResult.categories();
		var targetCategories = Categories.builder()
			.sexual(Boolean.TRUE.equals(sourceCategories.sexual()))
			.pii(Boolean.TRUE.equals(sourceCategories.pii()))
			.law(Boolean.TRUE.equals(sourceCategories.law()))
			.financial(Boolean.TRUE.equals(sourceCategories.financial()))
			.health(Boolean.TRUE.equals(sourceCategories.health()))
			.criminal(Boolean.TRUE.equals(sourceCategories.criminal()))
			.dangerous(Boolean.TRUE.equals(sourceCategories.dangerous()))
			.jailbreaking(Boolean.TRUE.equals(sourceCategories.jailbreaking()))
			.violence(Boolean.TRUE.equals(sourceCategories.violenceAndThreats()))
			.hate(Boolean.TRUE.equals(sourceCategories.hateAndDiscrimination()))
			.selfHarm(Boolean.TRUE.equals(sourceCategories.selfHarm()))
			.build();

		var sourceCategoryScores = mistralAiModerationResult.categoryScores();
		var targetCategoryScores = CategoryScores.builder()
			.sexual(Objects.requireNonNullElse(sourceCategoryScores.sexual(), 0.0))
			.pii(Objects.requireNonNullElse(sourceCategoryScores.pii(), 0.0))
			.law(Objects.requireNonNullElse(sourceCategoryScores.law(), 0.0))
			.financial(Objects.requireNonNullElse(sourceCategoryScores.financial(), 0.0))
			.health(Objects.requireNonNullElse(sourceCategoryScores.health(), 0.0))
			.criminal(Objects.requireNonNullElse(sourceCategoryScores.criminal(), 0.0))
			.dangerous(Objects.requireNonNullElse(sourceCategoryScores.dangerous(), 0.0))
			.jailbreaking(Objects.requireNonNullElse(sourceCategoryScores.jailbreaking(), 0.0))
			.violence(Objects.requireNonNullElse(sourceCategoryScores.violenceAndThreats(), 0.0))
			.hate(Objects.requireNonNullElse(sourceCategoryScores.hateAndDiscrimination(), 0.0))
			.selfHarm(Objects.requireNonNullElse(sourceCategoryScores.selfHarm(), 0.0))
			.build();

		return ModerationResult.builder()
			.categories(targetCategories)
			.categoryScores(targetCategoryScores)
			.flagged(mistralAiModerationResult.flagged())
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable MistralAiModerationApi mistralAiModerationApi;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private MistralAiModerationOptions options = MistralAiModerationOptions.builder()
			.model(MistralAiModerationApi.Model.MISTRAL_MODERATION.getValue())
			.build();

		public Builder mistralAiModerationApi(MistralAiModerationApi mistralAiModerationApi) {
			this.mistralAiModerationApi = mistralAiModerationApi;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder options(MistralAiModerationOptions options) {
			this.options = options;
			return this;
		}

		public MistralAiModerationModel build() {
			Assert.state(this.mistralAiModerationApi != null, "MistralAiModerationApi must not be null");
			return new MistralAiModerationModel(this.mistralAiModerationApi, this.retryTemplate, this.options);
		}

	}

}
