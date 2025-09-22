/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.metadata;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.ModalityTokenCount;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.lang.Nullable;

/**
 * Extended usage metadata for Google GenAI responses that includes thinking tokens,
 * cached content, tool-use tokens, and modality breakdowns.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoogleGenAiUsage extends DefaultUsage {

	@Nullable
	private final Integer thoughtsTokenCount;

	@Nullable
	private final Integer cachedContentTokenCount;

	@Nullable
	private final Integer toolUsePromptTokenCount;

	@Nullable
	private final List<GoogleGenAiModalityTokenCount> promptTokensDetails;

	@Nullable
	private final List<GoogleGenAiModalityTokenCount> candidatesTokensDetails;

	@Nullable
	private final List<GoogleGenAiModalityTokenCount> cacheTokensDetails;

	@Nullable
	private final List<GoogleGenAiModalityTokenCount> toolUsePromptTokensDetails;

	@Nullable
	private final GoogleGenAiTrafficType trafficType;

	/**
	 * Creates a new GoogleGenAiUsage instance with all extended metadata.
	 */
	public GoogleGenAiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens,
			@Nullable Integer thoughtsTokenCount, @Nullable Integer cachedContentTokenCount,
			@Nullable Integer toolUsePromptTokenCount,
			@Nullable List<GoogleGenAiModalityTokenCount> promptTokensDetails,
			@Nullable List<GoogleGenAiModalityTokenCount> candidatesTokensDetails,
			@Nullable List<GoogleGenAiModalityTokenCount> cacheTokensDetails,
			@Nullable List<GoogleGenAiModalityTokenCount> toolUsePromptTokensDetails,
			@Nullable GoogleGenAiTrafficType trafficType, @Nullable GenerateContentResponseUsageMetadata nativeUsage) {
		super(promptTokens, completionTokens, totalTokens, nativeUsage);
		this.thoughtsTokenCount = thoughtsTokenCount;
		this.cachedContentTokenCount = cachedContentTokenCount;
		this.toolUsePromptTokenCount = toolUsePromptTokenCount;
		this.promptTokensDetails = promptTokensDetails;
		this.candidatesTokensDetails = candidatesTokensDetails;
		this.cacheTokensDetails = cacheTokensDetails;
		this.toolUsePromptTokensDetails = toolUsePromptTokensDetails;
		this.trafficType = trafficType;
	}

	/**
	 * Creates a GoogleGenAiUsage instance from the Google GenAI SDK response metadata.
	 * @param usageMetadata the usage metadata from the Google GenAI SDK
	 * @return a new GoogleGenAiUsage instance with all available metadata
	 */
	public static GoogleGenAiUsage from(GenerateContentResponseUsageMetadata usageMetadata) {
		if (usageMetadata == null) {
			return new GoogleGenAiUsage(0, 0, 0, null, null, null, null, null, null, null, null, null);
		}

		Integer promptTokens = usageMetadata.promptTokenCount().orElse(0);
		Integer completionTokens = usageMetadata.candidatesTokenCount().orElse(0);
		Integer totalTokens = usageMetadata.totalTokenCount().orElse(0);
		Integer thoughtsTokens = usageMetadata.thoughtsTokenCount().orElse(null);
		Integer cachedContentTokens = usageMetadata.cachedContentTokenCount().orElse(null);
		Integer toolUsePromptTokens = usageMetadata.toolUsePromptTokenCount().orElse(null);

		List<GoogleGenAiModalityTokenCount> promptDetails = convertModalityDetails(usageMetadata.promptTokensDetails());
		List<GoogleGenAiModalityTokenCount> candidatesDetails = convertModalityDetails(
				usageMetadata.candidatesTokensDetails());
		List<GoogleGenAiModalityTokenCount> cacheDetails = convertModalityDetails(usageMetadata.cacheTokensDetails());
		List<GoogleGenAiModalityTokenCount> toolUseDetails = convertModalityDetails(
				usageMetadata.toolUsePromptTokensDetails());

		GoogleGenAiTrafficType trafficType = usageMetadata.trafficType().map(GoogleGenAiTrafficType::from).orElse(null);

		return new GoogleGenAiUsage(promptTokens, completionTokens, totalTokens, thoughtsTokens, cachedContentTokens,
				toolUsePromptTokens, promptDetails, candidatesDetails, cacheDetails, toolUseDetails, trafficType,
				usageMetadata);
	}

	private static List<GoogleGenAiModalityTokenCount> convertModalityDetails(
			Optional<List<ModalityTokenCount>> modalityTokens) {
		return modalityTokens.map(tokens -> tokens.stream().map(GoogleGenAiModalityTokenCount::from).toList())
			.orElse(null);
	}

	/**
	 * Returns the number of tokens present in thoughts output for thinking-enabled
	 * models.
	 * @return the thoughts token count, or null if not available
	 */
	@JsonProperty("thoughtsTokenCount")
	@Nullable
	public Integer getThoughtsTokenCount() {
		return this.thoughtsTokenCount;
	}

	/**
	 * Returns the number of tokens in the cached content.
	 * @return the cached content token count, or null if not available
	 */
	@JsonProperty("cachedContentTokenCount")
	@Nullable
	public Integer getCachedContentTokenCount() {
		return this.cachedContentTokenCount;
	}

	/**
	 * Returns the number of tokens present in tool-use prompts.
	 * @return the tool-use prompt token count, or null if not available
	 */
	@JsonProperty("toolUsePromptTokenCount")
	@Nullable
	public Integer getToolUsePromptTokenCount() {
		return this.toolUsePromptTokenCount;
	}

	/**
	 * Returns the list of modalities that were processed in the request input.
	 * @return the prompt tokens details by modality, or null if not available
	 */
	@JsonProperty("promptTokensDetails")
	@Nullable
	public List<GoogleGenAiModalityTokenCount> getPromptTokensDetails() {
		return this.promptTokensDetails;
	}

	/**
	 * Returns the list of modalities that were returned in the response.
	 * @return the candidates tokens details by modality, or null if not available
	 */
	@JsonProperty("candidatesTokensDetails")
	@Nullable
	public List<GoogleGenAiModalityTokenCount> getCandidatesTokensDetails() {
		return this.candidatesTokensDetails;
	}

	/**
	 * Returns the list of modalities of the cached content in the request input.
	 * @return the cache tokens details by modality, or null if not available
	 */
	@JsonProperty("cacheTokensDetails")
	@Nullable
	public List<GoogleGenAiModalityTokenCount> getCacheTokensDetails() {
		return this.cacheTokensDetails;
	}

	/**
	 * Returns the list of modalities that were processed for tool-use request inputs.
	 * @return the tool-use prompt tokens details by modality, or null if not available
	 */
	@JsonProperty("toolUsePromptTokensDetails")
	@Nullable
	public List<GoogleGenAiModalityTokenCount> getToolUsePromptTokensDetails() {
		return this.toolUsePromptTokensDetails;
	}

	/**
	 * Returns the traffic type showing whether a request consumes Pay-As-You-Go or
	 * Provisioned Throughput quota.
	 * @return the traffic type, or null if not available
	 */
	@JsonProperty("trafficType")
	@Nullable
	public GoogleGenAiTrafficType getTrafficType() {
		return this.trafficType;
	}

	@Override
	public String toString() {
		return "GoogleGenAiUsage{" + "promptTokens=" + getPromptTokens() + ", completionTokens=" + getCompletionTokens()
				+ ", totalTokens=" + getTotalTokens() + ", thoughtsTokenCount=" + this.thoughtsTokenCount
				+ ", cachedContentTokenCount=" + this.cachedContentTokenCount + ", toolUsePromptTokenCount="
				+ this.toolUsePromptTokenCount + ", trafficType=" + this.trafficType + '}';
	}

}
