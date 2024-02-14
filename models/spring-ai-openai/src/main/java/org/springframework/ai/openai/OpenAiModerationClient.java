/*
 * Copyright 2024-2024 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.moderation.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAiModerationClient is a class that implements the ModerationClient interface. It
 * provides a client for calling the OpenAI moderation generation API.
 *
 * @author Ahmed Yousri
 * @since 0.9.0
 */
public class OpenAiModerationClient implements ModerationClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OpenAiModerationOptions defaultOptions;

	private final OpenAiModerationApi openAiModerationApi;

	public final RetryTemplate retryTemplate = RetryTemplate.builder()
		.maxAttempts(10)
		.retryOn(OpenAiApi.OpenAiApiException.class)
		.exponentialBackoff(Duration.ofMillis(2000), 5, Duration.ofMillis(3 * 60000))
		.withListener(new RetryListener() {
			public <T extends Object, E extends Throwable> void onError(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				logger.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
			};
		})
		.build();

	public OpenAiModerationClient(OpenAiModerationApi openAiModerationApi) {
		Assert.notNull(openAiModerationApi, "OpenAiModerationApi must not be null");
		this.openAiModerationApi = openAiModerationApi;
	}

	public OpenAiModerationOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public OpenAiModerationClient withDefaultOptions(OpenAiModerationOptions defaultOptions) {
		this.defaultOptions = defaultOptions;
		return this;
	}

	@Override
	public ModerationResponse call(ModerationPrompt moderationPrompt) {
		return this.retryTemplate.execute(ctx -> {

			String instructions = moderationPrompt.getInstructions().getText();

			OpenAiModerationApi.OpenAiModerationRequest moderationRequest = new OpenAiModerationApi.OpenAiModerationRequest(
					instructions);

			if (this.defaultOptions != null) {
				moderationRequest = ModelOptionsUtils.merge(this.defaultOptions, moderationRequest,
						OpenAiModerationApi.OpenAiModerationRequest.class);
			}

			if (moderationPrompt.getOptions() != null) {
				moderationRequest = ModelOptionsUtils.merge(toOpenAiModerationOptions(moderationPrompt.getOptions()),
						moderationRequest, OpenAiModerationApi.OpenAiModerationRequest.class);
			}

			ResponseEntity<OpenAiModerationApi.OpenAiModerationResponse> moderationResponseEntity = this.openAiModerationApi
				.createModeration(moderationRequest);

			return convertResponse(moderationResponseEntity, moderationRequest);
		});
	}

	private ModerationResponse convertResponse(
			ResponseEntity<OpenAiModerationApi.OpenAiModerationResponse> moderationResponseEntity,
			OpenAiModerationApi.OpenAiModerationRequest openAiModerationRequest) {
		OpenAiModerationApi.OpenAiModerationResponse moderationApiResponse = moderationResponseEntity.getBody();
		if (moderationApiResponse == null) {
			logger.warn("No moderation response returned for request: {}", openAiModerationRequest);
			return new ModerationResponse(Generation.NULL);
		}

		List<ModerationResult> moderationResults = new ArrayList<>();
		if (moderationApiResponse.results() != null) {

			for (OpenAiModerationApi.OpenAiModerationResult result : moderationApiResponse.results()) {
				Categories categories = null;
				CategoryScores categoryScores = null;
				if (result.categories() != null) {
					categories = Categories.builder()
						.withSexual(result.categories().sexual())
						.withHate(result.categories().hate())
						.withHarassment(result.categories().harassment())
						.withSelfHarm(result.categories().selfHarm())
						.withSexualMinors(result.categories().sexualMinors())
						.withHateThreatening(result.categories().hateThreatening())
						.withViolenceGraphic(result.categories().violenceGraphic())
						.withSelfHarmIntent(result.categories().selfHarmIntent())
						.withSelfHarmInstructions(result.categories().selfHarmInstructions())
						.withHarassmentThreatening(result.categories().harassmentThreatening())
						.withViolence(result.categories().violence())
						.build();
				}
				if (result.categoryScores() != null) {
					categoryScores = CategoryScores.builder()
						.withHate(result.categoryScores().hate())
						.withHateThreatening(result.categoryScores().hateThreatening())
						.withHarassment(result.categoryScores().harassment())
						.withHarassmentThreatening(result.categoryScores().harassmentThreatening())
						.withSelfHarm(result.categoryScores().selfHarm())
						.withSelfHarmIntent(result.categoryScores().selfHarmIntent())
						.withSelfHarmInstructions(result.categoryScores().selfHarmInstructions())
						.withSexual(result.categoryScores().sexual())
						.withSexualMinors(result.categoryScores().sexualMinors())
						.withViolence(result.categoryScores().violence())
						.withViolenceGraphic(result.categoryScores().violenceGraphic())
						.build();
				}
				ModerationResult moderationResult = ModerationResult.builder()
					.withCategories(categories)
					.withCategoryScores(categoryScores)
					.withFlagged(result.flagged())
					.build();
				moderationResults.add(moderationResult);
			}

		}

		Moderation moderation = Moderation.builder()
			.withId(moderationApiResponse.id())
			.withModel(moderationApiResponse.model())
			.withResults(moderationResults)
			.build();

		return new ModerationResponse(new Generation(moderation));
	}

	/**
	 * Convert the {@link ModerationOptions} into {@link OpenAiModerationOptions}.
	 * @return the converted {@link OpenAiModerationOptions}.
	 */
	private OpenAiModerationOptions toOpenAiModerationOptions(ModerationOptions runtimeModerationOptions) {
		OpenAiModerationOptions.Builder openAiModerationOptionsBuilder = OpenAiModerationOptions.builder();
		if (runtimeModerationOptions != null) {
			// Handle portable moderation options
			if (runtimeModerationOptions.getModel() != null) {
				openAiModerationOptionsBuilder.withModel(runtimeModerationOptions.getModel());
			}
		}
		return openAiModerationOptionsBuilder.build();
	}

}
