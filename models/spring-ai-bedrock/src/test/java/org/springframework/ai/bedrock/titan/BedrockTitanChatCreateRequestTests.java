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
package org.springframework.ai.bedrock.titan;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class BedrockTitanChatCreateRequestTests {

	private TitanChatBedrockApi api = new TitanChatBedrockApi(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
			Duration.ofMinutes(2));

	@Test
	public void createRequestWithChatOptions() {

		var client = new BedrockTitanChatClient(api,
				BedrockTitanChatOptions.builder()
					.withTemperature(66.6f)
					.withTopP(0.66f)
					.withMaxTokenCount(666)
					.withStopSequences(List.of("stop1", "stop2"))
					.build());

		var request = client.createRequest(new Prompt("Test message content"));

		assertThat(request.inputText()).isNotEmpty();
		assertThat(request.textGenerationConfig().temperature()).isEqualTo(66.6f);
		assertThat(request.textGenerationConfig().topP()).isEqualTo(0.66f);
		assertThat(request.textGenerationConfig().maxTokenCount()).isEqualTo(666);
		assertThat(request.textGenerationConfig().stopSequences()).containsExactly("stop1", "stop2");

		request = client.createRequest(new Prompt("Test message content",
				BedrockTitanChatOptions.builder()
					.withTemperature(99.9f)
					.withTopP(0.99f)
					.withMaxTokenCount(999)
					.withStopSequences(List.of("stop3", "stop4"))
					.build()

		));

		assertThat(request.inputText()).isNotEmpty();
		assertThat(request.textGenerationConfig().temperature()).isEqualTo(99.9f);
		assertThat(request.textGenerationConfig().topP()).isEqualTo(0.99f);
		assertThat(request.textGenerationConfig().maxTokenCount()).isEqualTo(999);
		assertThat(request.textGenerationConfig().stopSequences()).containsExactly("stop3", "stop4");
	}

}
