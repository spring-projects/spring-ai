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

package org.springframework.ai.cohere.chat;

import java.util.List;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link CohereChatModel}.
 *
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = CohereChatModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
public class CohereChatModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	CohereChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForChatOperation() {
		var options = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.maxTokens(2048)
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
		var options = CohereChatOptions.builder()
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.maxTokens(2048)
			.stop(List.of("this-is-the-end"))
			.temperature(0.7)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();

		// With MessageAggregator, all chunks are aggregated into a single response
		// So we get the aggregated text from the last (or only) response
		ChatResponse lastChatResponse = responses.get(responses.size() - 1);
		String aggregatedResponse = lastChatResponse.getResult().getOutput().getText();
		assertThat(aggregatedResponse).isNotEmpty();

		ChatResponseMetadata responseMetadata = lastChatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata);
	}

	private void validate(ChatResponseMetadata responseMetadata) {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("chat " + CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.COHERE.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.RESPONSE_MODEL.asString(),
					StringUtils.hasText(responseMetadata.getModel()) ? responseMetadata.getModel()
							: KeyValue.NONE_VALUE)
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.doesNotHaveHighCardinalityKeyValueWithKey(HighCardinalityKeyNames.REQUEST_TOP_K.asString())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "1.0")
			.matches(contextView -> {
				var keyValue = contextView.getHighCardinalityKeyValues()
					.stream()
					.filter(tag -> tag.getKey().equals(HighCardinalityKeyNames.RESPONSE_ID.asString()))
					.findFirst();
				if (StringUtils.hasText(responseMetadata.getId())) {
					return keyValue.isPresent() && keyValue.get().getValue().equals(responseMetadata.getId());
				}
				else {
					return keyValue.isEmpty();
				}
			})
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), "[\"COMPLETE\"]")
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
		public CohereApi cohereApi() {
			return CohereApi.builder().apiKey(System.getenv("COHERE_API_KEY")).build();
		}

		@Bean
		public CohereChatModel cohereChatModel(CohereApi cohereApi, TestObservationRegistry observationRegistry) {
			return CohereChatModel.builder()
				.cohereApi(cohereApi)
				.defaultOptions(CohereChatOptions.builder().build())
				.retryTemplate(new RetryTemplate())
				.observationRegistry(observationRegistry)
				.build();
		}

	}

}
