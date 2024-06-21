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
package org.springframework.ai.bedrock.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class BedrockConverseApiIT {

	private BedrockConverseApi converseApi = new BedrockConverseApi(EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id());

	@Test
	public void testConverse() {
		ContentBlock contentBlock = ContentBlock.builder().text("Give me the names of 3 famous pirates?").build();

		Message message = Message.builder().content(contentBlock).role(ConversationRole.USER).build();

		ConverseRequest request = ConverseRequest.builder()
			.modelId("anthropic.claude-3-sonnet-20240229-v1:0")
			.messages(List.of(message))
			.build();

		ConverseResponse response = converseApi.converse(request);

		assertThat(response).isNotNull();
		assertThat(response.output()).isNotNull();
		assertThat(response.output().message()).isNotNull();
		assertThat(response.output().message().content()).isNotEmpty();
		assertThat(response.output().message().content().get(0).text()).contains("Blackbeard");
		assertThat(response.stopReason()).isNotNull();
		assertThat(response.usage()).isNotNull();
		assertThat(response.usage().inputTokens()).isGreaterThan(10);
		assertThat(response.usage().outputTokens()).isGreaterThan(30);
	}

	@Test
	public void testConverseStream() {
		ContentBlock contentBlock = ContentBlock.builder().text("Give me the names of 3 famous pirates?").build();

		Message message = Message.builder().content(contentBlock).role(ConversationRole.USER).build();

		ConverseStreamRequest request = ConverseStreamRequest.builder()
			.modelId("anthropic.claude-3-sonnet-20240229-v1:0")
			.messages(List.of(message))
			.build();

		Flux<ConverseStreamOutput> responseStream = converseApi.converseStream(request);

		List<ConverseStreamOutput> responseOutputs = responseStream.collectList().block();

		assertThat(responseOutputs).isNotNull();
		assertThat(responseOutputs).hasSizeGreaterThan(10);
	}

}
