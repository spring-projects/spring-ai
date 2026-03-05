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

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.MediaModality;
import com.google.genai.types.ModalityTokenCount;
import com.google.genai.types.TrafficType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GoogleGenAiUsage class.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiUsageTests {

	@Test
	void testBasicUsageExtraction() {
		// Create mock usage metadata
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);
		assertThat(usage.getThoughtsTokenCount()).isNull();
		assertThat(usage.getCachedContentTokenCount()).isNull();
		assertThat(usage.getToolUsePromptTokenCount()).isNull();
	}

	@Test
	void testThinkingTokensExtraction() {
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(175)
			.thoughtsTokenCount(25)
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(175);
		assertThat(usage.getThoughtsTokenCount()).isEqualTo(25);
	}

	@Test
	void testCachedContentTokensExtraction() {
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(200)
			.candidatesTokenCount(50)
			.totalTokenCount(250)
			.cachedContentTokenCount(80)
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getPromptTokens()).isEqualTo(200);
		assertThat(usage.getCachedContentTokenCount()).isEqualTo(80);
	}

	@Test
	void testToolUseTokensExtraction() {
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(180)
			.toolUsePromptTokenCount(30)
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getToolUsePromptTokenCount()).isEqualTo(30);
	}

	@Test
	void testModalityDetailsExtraction() {
		ModalityTokenCount textModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(100)
			.build();

		ModalityTokenCount imageModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.IMAGE))
			.tokenCount(50)
			.build();

		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(150)
			.candidatesTokenCount(50)
			.totalTokenCount(200)
			.promptTokensDetails(List.of(textModality, imageModality))
			.candidatesTokensDetails(List.of(textModality))
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getPromptTokensDetails()).hasSize(2);
		assertThat(usage.getPromptTokensDetails().get(0).getModality()).isEqualTo("TEXT");
		assertThat(usage.getPromptTokensDetails().get(0).getTokenCount()).isEqualTo(100);
		assertThat(usage.getPromptTokensDetails().get(1).getModality()).isEqualTo("IMAGE");
		assertThat(usage.getPromptTokensDetails().get(1).getTokenCount()).isEqualTo(50);

		assertThat(usage.getCandidatesTokensDetails()).hasSize(1);
		assertThat(usage.getCandidatesTokensDetails().get(0).getModality()).isEqualTo("TEXT");
	}

	@Test
	void testTrafficTypeExtraction() {
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.trafficType(new TrafficType(TrafficType.Known.ON_DEMAND))
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getTrafficType()).isEqualTo(GoogleGenAiTrafficType.ON_DEMAND);
	}

	@Test
	void testProvisionedThroughputTrafficType() {
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.trafficType(new TrafficType(TrafficType.Known.PROVISIONED_THROUGHPUT))
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		assertThat(usage.getTrafficType()).isEqualTo(GoogleGenAiTrafficType.PROVISIONED_THROUGHPUT);
	}

	@Test
	void testCompleteMetadataExtraction() {
		// Create modality details
		ModalityTokenCount textPrompt = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(80)
			.build();

		ModalityTokenCount imagePrompt = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.IMAGE))
			.tokenCount(20)
			.build();

		ModalityTokenCount textCandidate = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(50)
			.build();

		ModalityTokenCount cachedText = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(30)
			.build();

		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(200)
			.thoughtsTokenCount(25)
			.cachedContentTokenCount(30)
			.toolUsePromptTokenCount(25)
			.promptTokensDetails(List.of(textPrompt, imagePrompt))
			.candidatesTokensDetails(List.of(textCandidate))
			.cacheTokensDetails(List.of(cachedText))
			.trafficType(new TrafficType(TrafficType.Known.ON_DEMAND))
			.build();

		GoogleGenAiUsage usage = GoogleGenAiUsage.from(usageMetadata);

		// Verify all fields
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(200);
		assertThat(usage.getThoughtsTokenCount()).isEqualTo(25);
		assertThat(usage.getCachedContentTokenCount()).isEqualTo(30);
		assertThat(usage.getToolUsePromptTokenCount()).isEqualTo(25);
		assertThat(usage.getPromptTokensDetails()).hasSize(2);
		assertThat(usage.getCandidatesTokensDetails()).hasSize(1);
		assertThat(usage.getCacheTokensDetails()).hasSize(1);
		assertThat(usage.getTrafficType()).isEqualTo(GoogleGenAiTrafficType.ON_DEMAND);
		assertThat(usage.getNativeUsage()).isNotNull();
		assertThat(usage.getNativeUsage()).isInstanceOf(GenerateContentResponseUsageMetadata.class);
	}

	@Test
	void testNullUsageMetadata() {
		GoogleGenAiUsage usage = GoogleGenAiUsage.from(null);

		assertThat(usage.getPromptTokens()).isZero();
		assertThat(usage.getCompletionTokens()).isZero();
		assertThat(usage.getTotalTokens()).isZero();
		assertThat(usage.getThoughtsTokenCount()).isNull();
		assertThat(usage.getCachedContentTokenCount()).isNull();
		assertThat(usage.getToolUsePromptTokenCount()).isNull();
		assertThat(usage.getPromptTokensDetails()).isNull();
		assertThat(usage.getCandidatesTokensDetails()).isNull();
		assertThat(usage.getCacheTokensDetails()).isNull();
		assertThat(usage.getToolUsePromptTokensDetails()).isNull();
		assertThat(usage.getTrafficType()).isNull();
		assertThat(usage.getNativeUsage()).isNull();
	}

	@Test
	void testJsonSerialization() throws Exception {
		// Create usage without native object to test pure serialization
		GoogleGenAiUsage usage = new GoogleGenAiUsage(100, 50, 175, 25, 30, 15, null, null, null, null,
				GoogleGenAiTrafficType.ON_DEMAND, null);

		String json = JsonMapper.shared().writeValueAsString(usage);

		assertThat(json).contains("\"promptTokens\":100");
		assertThat(json).contains("\"completionTokens\":50");
		assertThat(json).contains("\"totalTokens\":175");
		assertThat(json).contains("\"thoughtsTokenCount\":25");
		assertThat(json).contains("\"cachedContentTokenCount\":30");
		assertThat(json).contains("\"toolUsePromptTokenCount\":15");
		assertThat(json).contains("\"trafficType\":\"ON_DEMAND\"");
	}

	@Test
	void testBackwardCompatibility() {
		// Test that GoogleGenAiUsage can be used as a Usage interface
		GenerateContentResponseUsageMetadata usageMetadata = GenerateContentResponseUsageMetadata.builder()
			.promptTokenCount(100)
			.candidatesTokenCount(50)
			.totalTokenCount(150)
			.thoughtsTokenCount(25)
			.build();

		org.springframework.ai.chat.metadata.Usage usage = GoogleGenAiUsage.from(usageMetadata);

		// These should work through the Usage interface
		assertThat(usage.getPromptTokens()).isEqualTo(100);
		assertThat(usage.getCompletionTokens()).isEqualTo(50);
		assertThat(usage.getTotalTokens()).isEqualTo(150);
		assertThat(usage.getNativeUsage()).isNotNull();
	}

}
