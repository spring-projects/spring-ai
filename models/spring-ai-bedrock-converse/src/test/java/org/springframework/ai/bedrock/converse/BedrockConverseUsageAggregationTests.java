/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.internal.MapDocument;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class BedrockConverseUsageAggregationTests {

	private @Mock BedrockRuntimeClient bedrockRuntimeClient;

	private @Mock BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private BedrockProxyChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		this.chatModel = new BedrockProxyChatModel(this.bedrockRuntimeClient, this.bedrockRuntimeAsyncClient,
				FunctionCallingOptions.builder().build(), null, List.of(), ObservationRegistry.NOOP);
	}

	@Test
	public void call() {
		ConverseResponse converseResponse = ConverseResponse.builder()

			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText("Response Content Block"))
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(16).outputTokens(14).totalTokens(30).build())
			.build();

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class))).willReturn(converseResponse);

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response Content Block");

		assertThat(result.getMetadata().getUsage().getPromptTokens()).isEqualTo(16);
		assertThat(result.getMetadata().getUsage().getGenerationTokens()).isEqualTo(14);
		assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
	}

	@Test
	public void callWithToolUse() {

		ConverseResponse converseResponseToolUse = ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText(
							"Certainly! I'd be happy to check the current weather in Paris for you, with the temperature in Celsius. To get this information, I'll use the getCurrentWeather function. Let me fetch that for you right away."),
							ContentBlock.fromToolUse(ToolUseBlock.builder()
								.toolUseId("tooluse_2SZuiUDkRbeGysun8O2Wag")
								.name("getCurrentWeather")
								.input(MapDocument.mapBuilder()
									.putString("location", "Paris, France")
									.putString("unit", "C")
									.build())
								.build()))

					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(445).outputTokens(119).totalTokens(564).build())
			.stopReason(StopReason.TOOL_USE)
			.metrics(ConverseMetrics.builder().latencyMs(3435L).build())
			.build();

		ConverseResponse converseResponseFinal = ConverseResponse.builder()
			.output(ConverseOutput.builder()
				.message(Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText(
							"""
									Based on the information from the weather tool, the current temperature in Paris, France is 15.0°C (Celsius).

									Please note that weather conditions can change throughout the day, so this temperature represents the current
									reading at the time of the request. If you need more detailed information about the weather in Paris, such as
									humidity, wind speed, or forecast for the coming days, please let me know, and I'll be happy to provide more
									details if that information is available through our weather service.
									"""))
					.build())
				.build())
			.usage(TokenUsage.builder().inputTokens(540).outputTokens(106).totalTokens(646).build())
			.stopReason(StopReason.END_TURN)
			.metrics(ConverseMetrics.builder().latencyMs(3435L).build())
			.build();

		given(this.bedrockRuntimeClient.converse(isA(ConverseRequest.class))).willReturn(converseResponseToolUse)
			.willReturn(converseResponseFinal);

		FunctionCallback functionCallback = FunctionCallback.builder()
			.function("getCurrentWeather", (Request request) -> "15.0°C")
			.description("Gets the weather in location")
			.inputType(Request.class)
			.build();

		var result = this.chatModel.call(new Prompt("What is the weather in Paris?",
				PortableFunctionCallingOptions.builder().withFunctionCallbacks(functionCallback).build()));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent())
			.isSameAs(converseResponseFinal.output().message().content().get(0).text());

		assertThat(result.getMetadata().getUsage().getPromptTokens()).isEqualTo(445 + 540);
		assertThat(result.getMetadata().getUsage().getGenerationTokens()).isEqualTo(119 + 106);
		assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(564 + 646);
	}

	@Test
	public void streamWithToolUse() {
		// TODO: Implement the test
	}

	public record Request(String location, String unit) {
	}

}
