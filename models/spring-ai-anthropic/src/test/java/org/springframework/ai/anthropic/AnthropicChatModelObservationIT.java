/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropic;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in {@link AnthropicChatModel}.
 *
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
@SpringBootTest(classes = AnthropicChatModelObservationIT.Config.class,
		properties = "spring.ai.retry.on-http-codes=429")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicChatModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	AnthropicChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForChatOperation() {
		var options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.maxTokens(2048)
			.stopSequences(List.of("this-is-the-end"))
			.temperature(0.7)
			.topK(1)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, "[\"end_turn\"]");
	}

	@Test
	void observationForStreamingChatOperation() {
		var options = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.maxTokens(2048)
			.stopSequences(List.of("this-is-the-end"))
			.temperature(0.7)
			.topK(1)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();
		assertThat(responses).hasSizeGreaterThan(3);

		String aggregatedResponse = responses.subList(0, responses.size() - 1)
			.stream()
			.filter(r -> r.getResult() != null)
			.map(r -> r.getResult().getOutput().getText())
			.collect(Collectors.joining());
		assertThat(aggregatedResponse).isNotEmpty();

		ChatResponse lastChatResponse = responses.get(responses.size() - 1);

		ChatResponseMetadata responseMetadata = lastChatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, "[\"end_turn\"]");
	}

	private void validate(ChatResponseMetadata responseMetadata, String finishReasons) {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("chat " + AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.ANTHROPIC.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					AnthropicApi.ChatModel.CLAUDE_HAIKU_4_5.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), responseMetadata.getModel())
			.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TOP_K.asString(), "1")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_ID.asString(), responseMetadata.getId())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), finishReasons)
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
		public AnthropicApi anthropicApi() {
			return AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build();
		}

		@Bean
		public AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi,
				TestObservationRegistry observationRegistry) {
			return new AnthropicChatModel(anthropicApi, AnthropicChatOptions.builder().build(),
					ToolCallingManager.builder().build(), new RetryTemplate(), observationRegistry);
		}

	}

}
