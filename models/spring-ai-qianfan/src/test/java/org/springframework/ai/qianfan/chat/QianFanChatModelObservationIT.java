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

package org.springframework.ai.qianfan.chat;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.qianfan.QianFanChatModel;
import org.springframework.ai.qianfan.QianFanChatOptions;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link QianFanChatModel}.
 *
 * @author Geng Rong
 */
@SpringBootTest(classes = QianFanChatModelObservationIT.Config.class)
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "QIANFAN_SECRET_KEY", matches = ".+") })
public class QianFanChatModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	QianFanChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForChatOperation() {

		var options = QianFanChatOptions.builder()
			.withModel(QianFanApi.ChatModel.ERNIE_Speed_8K.getValue())
			.withFrequencyPenalty(0.0)
			.withMaxTokens(2048)
			.withPresencePenalty(0.0)
			.withStop(List.of("this-is-the-end"))
			.withTemperature(0.7)
			.withTopP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getContent()).isNotEmpty();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata);
	}

	@Test
	void observationForStreamingChatOperation() {
		var options = QianFanChatOptions.builder()
			.withModel(QianFanApi.ChatModel.ERNIE_Speed_8K.getValue())
			.withFrequencyPenalty(0.0)
			.withMaxTokens(2048)
			.withPresencePenalty(0.0)
			.withStop(List.of("this-is-the-end"))
			.withTemperature(0.7)
			.withTopP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);

		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();

		String aggregatedResponse = responses.subList(0, responses.size() - 1)
			.stream()
			.map(r -> r.getResult().getOutput().getContent())
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
			.hasContextualNameEqualTo("chat " + QianFanApi.ChatModel.ERNIE_Speed_8K.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.QIANFAN.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					QianFanApi.ChatModel.ERNIE_Speed_8K.getValue())
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
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getGenerationTokens()))
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
		public QianFanApi qianFanApi() {
			return new QianFanApi(System.getenv("QIANFAN_API_KEY"), System.getenv("QIANFAN_SECRET_KEY"));
		}

		@Bean
		public QianFanChatModel qianFanChatModel(QianFanApi qianFanApi, TestObservationRegistry observationRegistry) {
			return new QianFanChatModel(qianFanApi, QianFanChatOptions.builder().build(),
					RetryTemplate.defaultInstance(), observationRegistry);
		}

	}

}
