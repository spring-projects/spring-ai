/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.cohere;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@RequiresAwsCredentials
class BedrockCohereEmbeddingModelIT {

	@Autowired
	private BedrockCohereEmbeddingModel embeddingModel;

	@MockitoSpyBean
	private CohereEmbeddingBedrockApi embeddingApi;

	@Autowired
	@Qualifier("embeddingModelStartTruncate")
	private BedrockCohereEmbeddingModel embeddingModelStartTruncate;

	@Test
	void singleEmbedding() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

	@Test
	void truncatesLongText() {
		String longText = "Hello World".repeat(300);
		assertThat(longText.length()).isGreaterThan(2048);

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of(longText));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

	@Test
	void truncatesMultipleLongTexts() {
		String longText1 = "Hello World".repeat(300);
		String longText2 = "Another Text".repeat(300);

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of(longText1, longText2));

		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

	@Test
	void verifyExactTruncationLength() {
		String longText = "x".repeat(3000);

		ArgumentCaptor<CohereEmbeddingBedrockApi.CohereEmbeddingRequest> requestCaptor = ArgumentCaptor
			.forClass(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.class);

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of(longText));

		verify(this.embeddingApi).embedding(requestCaptor.capture());
		CohereEmbeddingBedrockApi.CohereEmbeddingRequest capturedRequest = requestCaptor.getValue();

		assertThat(capturedRequest.texts()).hasSize(1);
		assertThat(capturedRequest.texts().get(0).length()).isLessThanOrEqualTo(2048);

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
	}

	@Test
	void truncatesLongTextFromStart() {
		String startMarker = "START_MARKER_";
		String endMarker = "_END_MARKER";
		String middlePadding = "x".repeat(2500); // Long enough to force truncation
		String longText = startMarker + middlePadding + endMarker;

		assertThat(longText.length()).isGreaterThan(2048);

		ArgumentCaptor<CohereEmbeddingBedrockApi.CohereEmbeddingRequest> requestCaptor = ArgumentCaptor
			.forClass(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.class);

		EmbeddingResponse embeddingResponse = this.embeddingModelStartTruncate.embedForResponse(List.of(longText));

		// Verify truncation behavior
		verify(this.embeddingApi).embedding(requestCaptor.capture());
		String truncatedText = requestCaptor.getValue().texts().get(0);
		assertThat(truncatedText.length()).isLessThanOrEqualTo(2048);
		assertThat(truncatedText).doesNotContain(startMarker);
		assertThat(truncatedText).endsWith(endMarker);

		// Verify embedding response
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModelStartTruncate.dimensions()).isEqualTo(1024);
	}

	@Test
	void batchEmbedding() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel
			.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

	@Test
	void embeddingWthOptions() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Hello World", "World is big and salvation is near"),
					BedrockCohereEmbeddingOptions.builder().inputType(InputType.SEARCH_DOCUMENT).build()));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public CohereEmbeddingBedrockApi cohereEmbeddingApi() {
			return new CohereEmbeddingBedrockApi(CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V3.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new JsonMapper(),
					Duration.ofMinutes(2));
		}

		@Bean("embeddingModel")
		public BedrockCohereEmbeddingModel cohereAiEmbedding(CohereEmbeddingBedrockApi cohereEmbeddingApi) {
			// custom model that uses the END truncation strategy, instead of the default
			// NONE.
			return new BedrockCohereEmbeddingModel(cohereEmbeddingApi,
					BedrockCohereEmbeddingOptions.builder()
						.inputType(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT)
						.truncate(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate.END)
						.build());
		}

		@Bean("embeddingModelStartTruncate")
		public BedrockCohereEmbeddingModel cohereAiEmbeddingStartTruncate(
				CohereEmbeddingBedrockApi cohereEmbeddingApi) {
			// custom model that uses the START truncation strategy, instead of the
			// default NONE.
			return new BedrockCohereEmbeddingModel(cohereEmbeddingApi,
					BedrockCohereEmbeddingOptions.builder()
						.inputType(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT)
						.truncate(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate.START)
						.build());
		}

	}

}
