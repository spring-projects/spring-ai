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

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatRequest;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponseChunk;
import org.springframework.ai.model.ModelOptionsUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@RequiresAwsCredentials
public class TitanChatBedrockApiIT {

	TitanChatBedrockApi titanBedrockApi = new TitanChatBedrockApi(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), ModelOptionsUtils.OBJECT_MAPPER,
			Duration.ofMinutes(2));

	TitanChatRequest titanChatRequest = TitanChatRequest.builder("Give me the names of 3 famous pirates?")
		.temperature(0.5)
		.topP(0.9)
		.maxTokenCount(100)
		.stopSequences(List.of("|"))
		.build();

	@Test
	public void chatCompletion() {
		TitanChatResponse response = this.titanBedrockApi.chatCompletion(this.titanChatRequest);
		assertThat(response.results()).hasSize(1);
		assertThat(response.results().get(0).outputText()).contains("Blackbeard");
	}

	@Test
	public void chatCompletionStream() {
		Flux<TitanChatResponseChunk> response = this.titanBedrockApi.chatCompletionStream(this.titanChatRequest);
		List<TitanChatResponseChunk> results = response.collectList().block();

		String combinedResponse = results.stream()
			.map(TitanChatResponseChunk::outputText)
			.collect(Collectors.joining(System.lineSeparator()));

		assertThat(combinedResponse)
			.matches(text -> text.contains("Teach") || text.contains("Blackbeard") || text.contains("Roberts")
					|| text.contains("Rackham") || text.contains("Morgan") || text.contains("Kidd"));
	}

}
