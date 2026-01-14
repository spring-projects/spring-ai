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

package org.springframework.ai.bedrock.cohere.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@RequiresAwsCredentials
public class CohereEmbeddingBedrockApiIT {

	CohereEmbeddingBedrockApi api = new CohereEmbeddingBedrockApi(
			CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V3.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(), new JsonMapper(), Duration.ofMinutes(2));

	@Test
	public void embedText() {

		CohereEmbeddingRequest request = new CohereEmbeddingRequest(
				List.of("I like to eat apples", "I like to eat oranges"),
				CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT, CohereEmbeddingRequest.Truncate.NONE);

		CohereEmbeddingResponse response = this.api.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.texts()).isEqualTo(request.texts());
		assertThat(response.embeddings()).hasSize(2);
		assertThat(response.embeddings().get(0)).hasSize(1024);
	}

	@Test
	public void embedTextWithTruncate() {

		CohereEmbeddingRequest request = new CohereEmbeddingRequest(
				List.of("I like to eat apples", "I like to eat oranges"),
				CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT, CohereEmbeddingRequest.Truncate.START);

		CohereEmbeddingResponse response = this.api.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.texts()).isEqualTo(request.texts());
		assertThat(response.embeddings()).hasSize(2);
		assertThat(response.embeddings().get(0)).hasSize(1024);

		request = new CohereEmbeddingRequest(List.of("I like to eat apples", "I like to eat oranges"),
				CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT, CohereEmbeddingRequest.Truncate.END);

		response = this.api.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.texts()).isEqualTo(request.texts());
		assertThat(response.embeddings()).hasSize(2);
		assertThat(response.embeddings().get(0)).hasSize(1024);
	}

}
