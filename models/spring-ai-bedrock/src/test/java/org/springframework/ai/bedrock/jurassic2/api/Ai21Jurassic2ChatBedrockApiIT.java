/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.bedrock.jurassic2.api;

import java.time.Duration;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatModel;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatRequest;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class Ai21Jurassic2ChatBedrockApiIT {

	Ai21Jurassic2ChatBedrockApi api = new Ai21Jurassic2ChatBedrockApi(Ai21Jurassic2ChatModel.AI21_J2_ULTRA_V1.id(),
			Region.US_EAST_1.id(), Duration.ofMinutes(2));

	@Test
	public void chatCompletion() {
		Ai21Jurassic2ChatRequest request = new Ai21Jurassic2ChatRequest("Give me the names of 3 famous pirates?", 0.9f,
				0.9f, 100, null, // List.of("END"),
				new Ai21Jurassic2ChatRequest.IntegerScalePenalty(1, true, true, true, true, true),
				new Ai21Jurassic2ChatRequest.FloatScalePenalty(0.5f, true, true, true, true, true),
				new Ai21Jurassic2ChatRequest.IntegerScalePenalty(1, true, true, true, true, true));

		Ai21Jurassic2ChatResponse response = api.chatCompletion(request);

		assertThat(response).isNotNull();
		assertThat(response.completions()).isNotEmpty();
		assertThat(response.amazonBedrockInvocationMetrics()).isNull();

		String responseContent = response.completions()
			.stream()
			.map(c -> c.data().text())
			.collect(Collectors.joining(System.lineSeparator()));
		assertThat(responseContent).contains("Blackbeard");
	}

	// Note: Ai21Jurassic2 doesn't support streaming yet!

}
