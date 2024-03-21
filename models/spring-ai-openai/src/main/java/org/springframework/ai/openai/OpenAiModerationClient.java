package org.springframework.ai.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiModerationApi.ModerationModel;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.ai.openai.api.OpenAiModerationApi.ModerationRequest;
import org.springframework.ai.openai.moderation.ModerationClient;
import org.springframework.ai.openai.moderation.ModerationGeneration;
import org.springframework.ai.openai.moderation.ModerationPrompt;
import org.springframework.ai.openai.moderation.ModerationResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * @author Ricken Bazolo
 */
public class OpenAiModerationClient implements ModerationClient {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAiModerationOptions defaultOptions;

	private final OpenAiModerationApi moderationApi;

	public final RetryTemplate retryTemplate;

	/**
	 * Initializes a new instance of the OpenAiModerationClient class with the provided
	 * @param moderationApi The OpenAiModerationApi to use for moderation.
	 */
	public OpenAiModerationClient(OpenAiModerationApi moderationApi) {
		this(moderationApi,
				OpenAiModerationOptions.builder().withModel(ModerationModel.TEXT_MODERATION_LATEST.getValue()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the OpenAiModerationClient class with the provided
	 * @param moderationApi The OpenAiModerationApi to use for moderation.
	 * @param options The OpenAiModerationOptions containing the moderation
	 * @param retryTemplate The RetryTemplate to use for retrying requests.
	 */
	public OpenAiModerationClient(OpenAiModerationApi moderationApi, OpenAiModerationOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(moderationApi, "OpenAiModerationApi must not be null");
		Assert.notNull(options, "OpenAiModerationOptions must not be null");
		this.defaultOptions = options;
		this.moderationApi = moderationApi;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ModerationResponse call(String message) {
		var prompt = new ModerationPrompt(message);
		return call(prompt);
	}

	@Override
	public ModerationResponse call(ModerationPrompt request) {
		return this.retryTemplate.execute(ctx -> {

			var moderationRequest = createModerationRequest(request);

			var responseEntity = this.moderationApi.createModeration(moderationRequest);

			var body = responseEntity.getBody();

			if (body == null) {
				logger.warn("No moderation response returned for moderationRequest: {}", moderationRequest);
				return new ModerationResponse(null);
			}

			return new ModerationResponse(new ModerationGeneration(body));

		});
	}

	/**
	 * Creates a ModerationRequest from the provided ModerationPrompt.
	 * @param request The ModerationPrompt to create a ModerationRequest from.
	 * @return The created ModerationRequest.
	 */
	private ModerationRequest createModerationRequest(ModerationPrompt request) {
		var options = this.defaultOptions;

		if (request.getOptions() != null) {
			if (request.getOptions() instanceof OpenAiModerationOptions runtimeOptions) {
				options = this.merge(options, runtimeOptions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type MediationOptions: "
						+ request.getOptions().getClass().getSimpleName());
			}
		}
		return ModerationRequest.builder().withInput(request.getInstructions()).withModel(options.getModel()).build();
	}

	private OpenAiModerationOptions merge(OpenAiModerationOptions source, OpenAiModerationOptions target) {
		return OpenAiModerationOptions.builder()
			.withModel(source.getModel() != null ? source.getModel() : target.getModel())
			.build();
	}

}
