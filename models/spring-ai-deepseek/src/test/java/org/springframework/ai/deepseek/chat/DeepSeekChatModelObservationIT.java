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

package org.springframework.ai.deepseek.chat;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link DeepSeekChatModel}.
 *
 * @author Geng Rong
 */
@SpringBootTest(classes = DeepSeekChatModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
public class DeepSeekChatModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	DeepSeekChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForChatOperation() {
		var options = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.frequencyPenalty(0.0)
			.maxTokens(2048)
			.presencePenalty(0.0)
			.stop(List.of("this-is-the-end"))
			.temperature(0.7)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata);
	}

	@Test
	void observationForStreamingChatOperation() {
		var options = DeepSeekChatOptions.builder()
			.model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.frequencyPenalty(0.0)
			.maxTokens(2048)
			.presencePenalty(0.0)
			.stop(List.of("this-is-the-end"))
			.temperature(0.7)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();
		assertThat(responses).hasSizeGreaterThan(10);

		String aggregatedResponse = responses.subList(0, responses.size() - 1)
			.stream()
			.map(r -> r.getResult().getOutput().getText())
			.collect(Collectors.joining());
		assertThat(aggregatedResponse).isNotEmpty();

		ChatResponse lastChatResponse = responses.get(responses.size() - 1);

		ChatResponseMetadata responseMetadata = lastChatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata);
	}

	private void validate(ChatResponseMetadata responseMetadata) {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("chat " + DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.DEEPSEEK.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), responseMetadata.getModel())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(), "0.0")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(), "0.0")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_TOP_K.asString())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "1.0")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_ID.asString(), responseMetadata.getId())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), "[\"STOP\"]")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getCompletionTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public DeepSeekApi deepSeekApi() {
			return DeepSeekApi.builder().apiKey(System.getenv("DEEPSEEK_API_KEY")).build();
		}

		@Bean
		public DeepSeekChatModel deepSeekChatModel(DeepSeekApi deepSeekApi,
				TestObservationRegistry observationRegistry) {
			return new DeepSeekChatModel(deepSeekApi, DeepSeekChatOptions.builder().build(),
					ToolCallingManager.builder().build(), RetryTemplate.defaultInstance(), observationRegistry);
		}

	}

}
