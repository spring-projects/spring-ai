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

package org.springframework.ai.bedrock.titan.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingRequest;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingResponse;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@RequiresAwsCredentials
public class TitanEmbeddingBedrockApiIT {

	@Test
	public void embedTextV1() {

		TitanEmbeddingBedrockApi titanEmbedApi = new TitanEmbeddingBedrockApi(
				TitanEmbeddingModel.TITAN_EMBED_TEXT_V1.id(), EnvironmentVariableCredentialsProvider.create(),
				Region.US_EAST_1.id(), new JsonMapper(), Duration.ofMinutes(2));

		TitanEmbeddingRequest request = TitanEmbeddingRequest.builder().inputText("I like to eat apples.").build();

		TitanEmbeddingResponse response = titanEmbedApi.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.inputTextTokenCount()).isEqualTo(6);
		assertThat(response.embedding()).hasSize(1536);
	}

	@Test
	public void embedTextV2() {

		TitanEmbeddingBedrockApi titanEmbedApi = new TitanEmbeddingBedrockApi(
				TitanEmbeddingModel.TITAN_EMBED_TEXT_V2.id(), EnvironmentVariableCredentialsProvider.create(),
				Region.US_EAST_1.id(), new JsonMapper(), Duration.ofMinutes(2));

		TitanEmbeddingRequest request = TitanEmbeddingRequest.builder().inputText("I like to eat apples.").build();

		TitanEmbeddingResponse response = titanEmbedApi.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.inputTextTokenCount()).isEqualTo(7);
		assertThat(response.embedding()).hasSize(1024);
	}

	@Test
	public void embedImage() throws IOException {

		TitanEmbeddingBedrockApi titanEmbedApi = new TitanEmbeddingBedrockApi(
				TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(), EnvironmentVariableCredentialsProvider.create(),
				Region.US_EAST_1.id(), new JsonMapper(), Duration.ofMinutes(2));

		byte[] image = new DefaultResourceLoader().getResource("classpath:/spring_framework.png")
			.getContentAsByteArray();

		String imageBase64 = Base64.getEncoder().encodeToString(image);
		System.out.println(imageBase64.length());

		TitanEmbeddingRequest request = TitanEmbeddingRequest.builder().inputImage(imageBase64).build();

		TitanEmbeddingResponse response = titanEmbedApi.embedding(request);

		assertThat(response).isNotNull();
		assertThat(response.inputTextTokenCount()).isEqualTo(0); // e.g. image input
		assertThat(response.embedding()).hasSize(1024);
	}

}
