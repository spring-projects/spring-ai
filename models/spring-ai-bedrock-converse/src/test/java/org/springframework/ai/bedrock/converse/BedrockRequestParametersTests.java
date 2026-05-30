/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.bedrock.converse;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that requestParameters set in defaultOptions are propagated to the
 * ConverseRequest even when the prompt itself provides no requestParameters (GH-5959).
 */
@ExtendWith(MockitoExtension.class)
class BedrockRequestParametersTests {

	@Mock
	private BedrockRuntimeClient bedrockRuntimeClient;

	@Mock
	private BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private ConverseResponse okResponse() {
		return ConverseResponse.builder()
			.output(output -> output.message(
					Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromText("ok")).build()))
			.usage(TokenUsage.builder().inputTokens(10).outputTokens(5).totalTokens(15).build())
			.stopReason(StopReason.END_TURN)
			.build();
	}

	@Test
	void defaultOptionsRequestParametersArePassedToConverseRequest() {
		when(this.bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(okResponse());

		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.bedrockRuntimeClient(this.bedrockRuntimeClient)
			.bedrockRuntimeAsyncClient(this.bedrockRuntimeAsyncClient)
			.defaultOptions(BedrockChatOptions.builder()
				.model("us.anthropic.claude-sonnet-4-6")
				.requestParameters(Map.of("thinking", "{\"type\":\"adaptive\",\"effort\":\"low\"}"))
				.build())
			.build();

		chatModel.call(new Prompt(new UserMessage("Explain recursion briefly")));

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(this.bedrockRuntimeClient).converse(captor.capture());

		assertThat(captor.getValue().additionalModelRequestFields().asMap()).containsKey("thinking");
	}

	@Test
	void defaultOptionsRequestParametersArePreservedWhenPromptOptionsHaveNone() {
		when(this.bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(okResponse());

		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.bedrockRuntimeClient(this.bedrockRuntimeClient)
			.bedrockRuntimeAsyncClient(this.bedrockRuntimeAsyncClient)
			.defaultOptions(BedrockChatOptions.builder()
				.model("us.anthropic.claude-sonnet-4-6")
				.requestParameters(Map.of("thinking", "{\"type\":\"adaptive\"}"))
				.build())
			.build();

		// Prompt provides options without requestParameters — default ones must survive
		chatModel.call(new Prompt(new UserMessage("hi"),
				BedrockChatOptions.builder().model("us.anthropic.claude-sonnet-4-6").maxTokens(100).build()));

		ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
		verify(this.bedrockRuntimeClient).converse(captor.capture());

		assertThat(captor.getValue().additionalModelRequestFields().asMap()).containsKey("thinking");
	}

}
