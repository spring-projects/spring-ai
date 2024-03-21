package org.springframework.ai.openai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Checking input is potentially harmful. Based on the OpenAI Moderation API.
 * <a href="https://platform.openai.com/docs/api-reference/moderations">OpenAI
 * Moderation</a>
 *
 * @author Ricken Bazolo
 */
public class OpenAiModerationApi {

	private final RestClient restClient;

	/**
	 * Create an new moderation api.
	 * @param openAiToken OpenAI apiKey.
	 */
	public OpenAiModerationApi(String openAiToken) {
		this(ApiUtils.DEFAULT_BASE_URL, openAiToken, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create an new moderation api.
	 * @param baseUrl api base URL.
	 * @param openAiToken OpenAI apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public OpenAiModerationApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(headers -> {
			headers.setBearerAuth(openAiToken);
		}).defaultStatusHandler(responseErrorHandler).build();

	}

	/**
	 * OpenAI moderation models.
	 *
	 * Two content moderations models are available: text-moderation-stable and
	 * text-moderation-latest. The default is text-moderation-latest which will be
	 * automatically upgraded over time. This ensures you are always using our most
	 * accurate model. If you use input-moderation-stable, we will provide advanced notice
	 * before updating the model. Accuracy of input-moderation-stable may be slightly
	 * lower than for input-moderation-latest.
	 */
	public enum ModerationModel {

		TEXT_MODERATION_STABLE("text-moderation-stable"), TEXT_MODERATION_LATEST("text-moderation-latest");

		private final String value;

		ModerationModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

	}

	/**
	 * Request to generates moderation from the input. Reference:
	 * <a href="https://platform.openai.com/docs/api-reference/moderations/create">Create
	 * moderation</a>
	 *
	 * @param input The input text to classify
	 * @param model he model to use for generating the moderation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ModerationRequest(@JsonProperty("input") String input, @JsonProperty("model") String model) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String input;

			private String model = ModerationModel.TEXT_MODERATION_LATEST.getValue();

			public Builder withInput(String input) {
				this.input = input;
				return this;
			}

			public Builder withModel(String model) {
				this.model = model;
				return this;
			}

			public ModerationRequest build() {
				return new ModerationRequest(this.input, this.model);
			}

		}
	}

	/**
	 * Response from the moderation request. Represents if a given text input is
	 * potentially harmful.
	 *
	 * @param id The unique identifier for the moderation request.
	 * @param model The model used to generate the moderation results.
	 * @param results A list of moderation objects.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ModerationObject(@JsonProperty("id") String id, @JsonProperty("model") String model,
			@JsonProperty("results") List<Result> results) {

		/**
		 * Moderation result.
		 *
		 * @param flagged Whether any of the below categories are flagged.
		 * @param categories A list of the categories, and whether they are flagged or
		 * not.
		 * @param categoryScores A list of the categories along with their scores as
		 * predicted by model.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Result(@JsonProperty("flagged") Boolean flagged, @JsonProperty("categories") Category categories,
				@JsonProperty("category_scores") CategoryScore categoryScores) {

			/**
			 * Moderation categories.
			 *
			 * @param hate Content that expresses, incites, or promotes hate based on
			 * race, gender, ethnicity, religion, nationality, sexual orientation,
			 * disability status, or caste. Hateful content aimed at non-protected groups
			 * (e.g., chess players) is harassment.
			 * @param hateThreatening Hateful content that also includes violence or
			 * serious harm towards the targeted group based on race, gender, ethnicity,
			 * religion, nationality, sexual orientation, disability status, or caste.
			 * @param harassment Content that expresses, incites, or promotes harassing
			 * language towards any target.
			 * @param harassmentThreatening Harassment content that also includes violence
			 * or serious harm towards any target.
			 * @param selfHarm Content that promotes, encourages, or depicts acts of
			 * self-harm, such as suicide, cutting, and eating disorders.
			 * @param selfHarmIntent Content where the speaker expresses that they are
			 * engaging or intend to engage in acts of self-harm, such as suicide,
			 * cutting, and eating disorders.
			 * @param selfHarmInstructions Content that encourages performing acts of
			 * self-harm, such as suicide, cutting, and eating disorders, or that gives
			 * instructions or advice on how to commit such acts.
			 * @param sexual Content meant to arouse sexual excitement, such as the
			 * description of sexual activity, or that promotes sexual services (excluding
			 * sex education and wellness).
			 * @param sexualMinors Sexual content that includes an individual who is under
			 * 18 years old.
			 * @param violence Content that depicts death, violence, or physical injury.
			 * @param violenceGraphic Content that depicts death, violence, or physical
			 * injury in graphic detail.
			 */
			@JsonInclude(Include.NON_NULL)
			public record Category(@JsonProperty("hate") Boolean hate,
					@JsonProperty("hate/threatening") Boolean hateThreatening,
					@JsonProperty("harassment") Boolean harassment,
					@JsonProperty("harassment/threatening") Boolean harassmentThreatening,
					@JsonProperty("self-harm") Boolean selfHarm,
					@JsonProperty("self-harm/intent") Boolean selfHarmIntent,
					@JsonProperty("self-harm/instructions") Boolean selfHarmInstructions,
					@JsonProperty("sexual") Boolean sexual, @JsonProperty("sexual/minors") Boolean sexualMinors,
					@JsonProperty("violence") Boolean violence,
					@JsonProperty("violence/graphic") Boolean violenceGraphic) {
			}

			/**
			 * Moderation category scores.
			 *
			 * @param hate The score for the category 'hate'.
			 * @param hateThreatening The score for the category 'hate/threatening'.
			 * @param harassment The score for the category 'harassment'.
			 * @param harassmentThreatening The score for the category
			 * 'harassment/threatening'.
			 * @param selfHarm The score for the category 'self-harm'.
			 * @param selfHarmIntent The score for the category 'self-harm/intent'.
			 * @param selfHarmInstructions The score for the category
			 * 'self-harm/instructions'.
			 * @param sexual The score for the category 'sexual'.
			 * @param sexualMinors The score for the category 'sexual/minors'.
			 * @param violence The score for the category 'violence'.
			 * @param violenceGraphic The score for the category 'violence/graphic'.
			 */
			@JsonInclude(Include.NON_NULL)
			public record CategoryScore(@JsonProperty("hate") Double hate,
					@JsonProperty("hate/threatening") Double hateThreatening,
					@JsonProperty("harassment") Double harassment,
					@JsonProperty("harassment/threatening") Double harassmentThreatening,
					@JsonProperty("self-harm") Double selfHarm, @JsonProperty("self-harm/intent") Double selfHarmIntent,
					@JsonProperty("self-harm/instructions") Double selfHarmInstructions,
					@JsonProperty("sexual") Double sexual, @JsonProperty("sexual/minors") Double sexualMinors,
					@JsonProperty("violence") Double violence,
					@JsonProperty("violence/graphic") Double violenceGraphic) {
			}
		}

	}

	/**
	 * Create a new moderation request from input
	 * @param requestBody The request body
	 * @return Response entity containing The moderation object, represents if a given
	 * text input is potentially harmful.
	 */
	public ResponseEntity<ModerationObject> createModeration(ModerationRequest requestBody) {
		return this.restClient.post()
			.uri("/v1/moderations")
			.body(requestBody)
			.retrieve()
			.toEntity(ModerationObject.class);
	}

}
