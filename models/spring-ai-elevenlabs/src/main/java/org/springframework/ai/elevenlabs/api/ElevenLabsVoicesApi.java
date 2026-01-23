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

package org.springframework.ai.elevenlabs.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Client for the ElevenLabs Voices API.
 *
 * @author Alexandros Pappas
 */
public class ElevenLabsVoicesApi {

	private static final String DEFAULT_BASE_URL = "https://api.elevenlabs.io";

	private final RestClient restClient;

	/**
	 * Create a new ElevenLabs Voices API client.
	 * @param baseUrl The base URL for the ElevenLabs API.
	 * @param apiKey Your ElevenLabs API key.
	 * @param headers the http headers to use.
	 * @param restClientBuilder A builder for the Spring RestClient.
	 * @param responseErrorHandler A custom error handler for API responses.
	 */
	public ElevenLabsVoicesApi(String baseUrl, ApiKey apiKey, HttpHeaders headers, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		Consumer<HttpHeaders> jsonContentHeaders = h -> {
			if (!(apiKey instanceof NoopApiKey)) {
				h.set("xi-api-key", apiKey.getValue());
			}
			h.addAll(HttpHeaders.readOnlyHttpHeaders(headers));
			h.setContentType(MediaType.APPLICATION_JSON);
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

	}

	/**
	 * Create a new ElevenLabs Voices API client.
	 * @param restClient Spring RestClient instance.
	 */
	public ElevenLabsVoicesApi(RestClient restClient) {
		this.restClient = restClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Retrieves a list of all available voices from the ElevenLabs API.
	 * @return A ResponseEntity containing a Voices object, which contains the list of
	 * voices.
	 */
	public ResponseEntity<Voices> getVoices() {
		return this.restClient.get().uri("/v1/voices").retrieve().toEntity(Voices.class);
	}

	/**
	 * Gets the default settings for voices. "similarity_boost" corresponds to ”Clarity +
	 * Similarity Enhancement” in the web app and "stability" corresponds to "Stability"
	 * slider in the web app.
	 * @return {@link ResponseEntity} containing the {@link VoiceSettings} record.
	 */
	public ResponseEntity<VoiceSettings> getDefaultVoiceSettings() {
		return this.restClient.get().uri("/v1/voices/settings/default").retrieve().toEntity(VoiceSettings.class);
	}

	/**
	 * Returns the settings for a specific voice. "similarity_boost" corresponds to
	 * "Clarity + Similarity Enhancement" in the web app and "stability" corresponds to
	 * the "Stability" slider in the web app.
	 * @param voiceId The ID of the voice to get settings for. Required.
	 * @return {@link ResponseEntity} containing the {@link VoiceSettings} record.
	 */
	public ResponseEntity<VoiceSettings> getVoiceSettings(String voiceId) {
		Assert.hasText(voiceId, "voiceId cannot be null or empty");
		return this.restClient.get()
			.uri("/v1/voices/{voiceId}/settings", voiceId)
			.retrieve()
			.toEntity(VoiceSettings.class);
	}

	/**
	 * Returns metadata about a specific voice.
	 * @param voiceId ID of the voice to be used. You can use the Get voices endpoint list
	 * all the available voices. Required.
	 * @return {@link ResponseEntity} containing the {@link Voice} record.
	 */
	public ResponseEntity<Voice> getVoice(String voiceId) {
		Assert.hasText(voiceId, "voiceId cannot be null or empty");
		return this.restClient.get().uri("/v1/voices/{voiceId}", voiceId).retrieve().toEntity(Voice.class);
	}

	public enum CategoryEnum {

		@JsonProperty("generated")
		GENERATED("generated"), @JsonProperty("cloned")
		CLONED("cloned"), @JsonProperty("premade")
		PREMADE("premade"), @JsonProperty("professional")
		PROFESSIONAL("professional"), @JsonProperty("famous")
		FAMOUS("famous"), @JsonProperty("high_quality")
		HIGH_QUALITY("high_quality");

		public final String value;

		CategoryEnum(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

	}

	public enum SafetyControlEnum {

		@JsonProperty("NONE")
		NONE("NONE"), @JsonProperty("BAN")
		BAN("BAN"), @JsonProperty("CAPTCHA")
		CAPTCHA("CAPTCHA"), @JsonProperty("CAPTCHA_AND_MODERATION")
		CAPTCHA_AND_MODERATION("CAPTCHA_AND_MODERATION"), @JsonProperty("ENTERPRISE_BAN")
		ENTERPRISE_BAN("ENTERPRISE_BAN"), @JsonProperty("ENTERPRISE_CAPTCHA")
		ENTERPRISE_CAPTCHA("ENTERPRISE_CAPTCHA");

		public final String value;

		SafetyControlEnum(String value) {
			this.value = value;
		}

		@JsonValue
		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Represents the response from the /v1/voices endpoint.
	 *
	 * @param voices A list of Voice objects representing the available voices.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Voices(@JsonProperty("voices") List<Voice> voices) {
	}

	/**
	 * Represents a single voice from the ElevenLabs API.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Voice(@JsonProperty("voice_id") String voiceId, @JsonProperty("name") String name,
			@JsonProperty("samples") List<Sample> samples, @JsonProperty("category") CategoryEnum category,
			@JsonProperty("fine_tuning") FineTuning fineTuning, @JsonProperty("labels") Map<String, String> labels,
			@JsonProperty("description") String description, @JsonProperty("preview_url") String previewUrl,
			@JsonProperty("available_for_tiers") List<String> availableForTiers,
			@JsonProperty("settings") VoiceSettings settings, @JsonProperty("sharing") VoiceSharing sharing,
			@JsonProperty("high_quality_base_model_ids") List<String> highQualityBaseModelIds,
			@JsonProperty("verified_languages") List<VerifiedVoiceLanguage> verifiedLanguages,
			@JsonProperty("safety_control") SafetyControlEnum safetyControl,
			@JsonProperty("voice_verification") VoiceVerification voiceVerification,
			@JsonProperty("permission_on_resource") String permissionOnResource,
			@JsonProperty("is_owner") Boolean isOwner, @JsonProperty("is_legacy") Boolean isLegacy,
			@JsonProperty("is_mixed") Boolean isMixed, @JsonProperty("created_at_unix") Integer createdAtUnix) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Sample(@JsonProperty("sample_id") String sampleId, @JsonProperty("file_name") String fileName,
			@JsonProperty("mime_type") String mimeType, @JsonProperty("size_bytes") Integer sizeBytes,
			@JsonProperty("hash") String hash) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FineTuning(@JsonProperty("is_allowed_to_fine_tune") Boolean isAllowedToFineTune,
			@JsonProperty("state") Map<String, String> state,
			@JsonProperty("verification_failures") List<String> verificationFailures,
			@JsonProperty("verification_attempts_count") Integer verificationAttemptsCount,
			@JsonProperty("manual_verification_requested") Boolean manualVerificationRequested,
			@JsonProperty("language") String language, @JsonProperty("progress") Map<String, Double> progress,
			@JsonProperty("message") Map<String, String> message,
			@JsonProperty("dataset_duration_seconds") Double datasetDurationSeconds,
			@JsonProperty("verification_attempts") List<VerificationAttempt> verificationAttempts,
			@JsonProperty("slice_ids") List<String> sliceIds,
			@JsonProperty("manual_verification") ManualVerification manualVerification,
			@JsonProperty("max_verification_attempts") Integer maxVerificationAttempts,
			@JsonProperty("next_max_verification_attempts_reset_unix_ms") Long nextMaxVerificationAttemptsResetUnixMs) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VoiceVerification(@JsonProperty("requires_verification") Boolean requiresVerification,
			@JsonProperty("is_verified") Boolean isVerified,
			@JsonProperty("verification_failures") List<String> verificationFailures,
			@JsonProperty("verification_attempts_count") Integer verificationAttemptsCount,
			@JsonProperty("language") String language,
			@JsonProperty("verification_attempts") List<VerificationAttempt> verificationAttempts) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VerificationAttempt(@JsonProperty("text") String text, @JsonProperty("date_unix") Integer dateUnix,
			@JsonProperty("accepted") Boolean accepted, @JsonProperty("similarity") Double similarity,
			@JsonProperty("levenshtein_distance") Double levenshteinDistance,
			@JsonProperty("recording") Recording recording) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Recording(@JsonProperty("recording_id") String recordingId,
			@JsonProperty("mime_type") String mimeType, @JsonProperty("size_bytes") Integer sizeBytes,
			@JsonProperty("upload_date_unix") Integer uploadDateUnix,
			@JsonProperty("transcription") String transcription) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ManualVerification(@JsonProperty("extra_text") String extraText,
			@JsonProperty("request_time_unix") Integer requestTimeUnix,
			@JsonProperty("files") List<ManualVerificationFile> files) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ManualVerificationFile(@JsonProperty("file_id") String fileId,
			@JsonProperty("file_name") String fileName, @JsonProperty("mime_type") String mimeType,
			@JsonProperty("size_bytes") Integer sizeBytes, @JsonProperty("upload_date_unix") Integer uploadDateUnix) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VoiceSettings(@JsonProperty("stability") Double stability,
			@JsonProperty("similarity_boost") Double similarityBoost, @JsonProperty("style") Double style,
			@JsonProperty("use_speaker_boost") Boolean useSpeakerBoost, @JsonProperty("speed") Double speed) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VoiceSharing(@JsonProperty("status") StatusEnum status,
			@JsonProperty("history_item_sample_id") String historyItemSampleId,
			@JsonProperty("date_unix") Integer dateUnix,
			@JsonProperty("whitelisted_emails") List<String> whitelistedEmails,
			@JsonProperty("public_owner_id") String publicOwnerId,
			@JsonProperty("original_voice_id") String originalVoiceId,
			@JsonProperty("financial_rewards_enabled") Boolean financialRewardsEnabled,
			@JsonProperty("free_users_allowed") Boolean freeUsersAllowed,
			@JsonProperty("live_moderation_enabled") Boolean liveModerationEnabled, @JsonProperty("rate") Double rate,
			@JsonProperty("notice_period") Integer noticePeriod, @JsonProperty("disable_at_unix") Integer disableAtUnix,
			@JsonProperty("voice_mixing_allowed") Boolean voiceMixingAllowed,
			@JsonProperty("featured") Boolean featured, @JsonProperty("category") CategoryEnum category,
			@JsonProperty("reader_app_enabled") Boolean readerAppEnabled, @JsonProperty("image_url") String imageUrl,
			@JsonProperty("ban_reason") String banReason, @JsonProperty("liked_by_count") Integer likedByCount,
			@JsonProperty("cloned_by_count") Integer clonedByCount, @JsonProperty("name") String name,
			@JsonProperty("description") String description, @JsonProperty("labels") Map<String, String> labels,
			@JsonProperty("review_status") ReviewStatusEnum reviewStatus,
			@JsonProperty("review_message") String reviewMessage,
			@JsonProperty("enabled_in_library") Boolean enabledInLibrary,
			@JsonProperty("instagram_username") String instagramUsername,
			@JsonProperty("twitter_username") String twitterUsername,
			@JsonProperty("youtube_username") String youtubeUsername,
			@JsonProperty("tiktok_username") String tiktokUsername,
			@JsonProperty("moderation_check") VoiceSharingModerationCheck moderationCheck,
			@JsonProperty("reader_restricted_on") List<ReaderResource> readerRestrictedOn) {
		public enum StatusEnum {

			@JsonProperty("enabled")
			ENABLED("enabled"), @JsonProperty("disabled")
			DISABLED("disabled"), @JsonProperty("copied")
			COPIED("copied"), @JsonProperty("copied_disabled")
			COPIED_DISABLED("copied_disabled");

			public final String value;

			StatusEnum(String value) {
				this.value = value;
			}

			@JsonValue
			public String getValue() {
				return this.value;
			}

		}

		public enum CategoryEnum {

			@JsonProperty("generated")
			GENERATED("generated"), @JsonProperty("professional")
			PROFESSIONAL("professional"), @JsonProperty("high_quality")
			HIGH_QUALITY("high_quality"), @JsonProperty("famous")
			FAMOUS("famous");

			public final String value;

			CategoryEnum(String value) {
				this.value = value;
			}

			@JsonValue
			public String getValue() {
				return this.value;
			}

		}

		public enum ReviewStatusEnum {

			@JsonProperty("not_requested")
			NOT_REQUESTED("not_requested"), @JsonProperty("pending")
			PENDING("pending"), @JsonProperty("declined")
			DECLINED("declined"), @JsonProperty("allowed")
			ALLOWED("allowed"), @JsonProperty("allowed_with_changes")
			ALLOWED_WITH_CHANGES("allowed_with_changes");

			public final String value;

			ReviewStatusEnum(String value) {
				this.value = value;
			}

			@JsonValue
			public String getValue() {
				return this.value;
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VoiceSharingModerationCheck(@JsonProperty("date_checked_unix") Integer dateCheckedUnix,
			@JsonProperty("name_value") String nameValue, @JsonProperty("name_check") Boolean nameCheck,
			@JsonProperty("description_value") String descriptionValue,
			@JsonProperty("description_check") Boolean descriptionCheck,
			@JsonProperty("sample_ids") List<String> sampleIds,
			@JsonProperty("sample_checks") List<Double> sampleChecks,
			@JsonProperty("captcha_ids") List<String> captchaIds,
			@JsonProperty("captcha_checks") List<Double> captchaChecks) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ReaderResource(@JsonProperty("resource_type") ResourceTypeEnum resourceType,
			@JsonProperty("resource_id") String resourceId) {

		public enum ResourceTypeEnum {

			@JsonProperty("read")
			READ("read"), @JsonProperty("collection")
			COLLECTION("collection");

			public final String value;

			ResourceTypeEnum(String value) {
				this.value = value;
			}

			@JsonValue
			public String getValue() {
				return this.value;
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record VerifiedVoiceLanguage(@JsonProperty("language") String language,
			@JsonProperty("model_id") String modelId, @JsonProperty("accent") String accent) {
	}

	/**
	 * Builder to construct {@link ElevenLabsVoicesApi} instance.
	 */
	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private HttpHeaders headers = new HttpHeaders();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(HttpHeaders headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public ElevenLabsVoicesApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new ElevenLabsVoicesApi(this.baseUrl, this.apiKey, this.headers, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

}
