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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.stream.Collectors;

import com.google.genai.Client;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Soby Chacko
 * @author Dan Dobrin
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
public class GoogleGenAiChatModelObservationApiKeyIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	GoogleGenAiChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForChatOperation() {

		var options = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue())
			.temperature(0.7)
			.stopSequences(List.of("this-is-the-end"))
			.maxOutputTokens(2048)
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
	void observationForStreamingOperation() {

		var options = GoogleGenAiChatOptions.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue())
			.temperature(0.7)
			.stopSequences(List.of("this-is-the-end"))
			.maxOutputTokens(2048)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponse = this.chatModel.stream(prompt);
		List<ChatResponse> responses = chatResponse.collectList().block();
		assertThat(responses).isNotEmpty();
		assertThat(responses).hasSizeGreaterThan(1);

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
			.hasLowCardinalityKeyValue(
					ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.GOOGLE_GENAI_AI.value())
			.hasLowCardinalityKeyValue(
					ChatModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW.getValue())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.doesNotHaveHighCardinalityKeyValueWithKey(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K.asString())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "1.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					"[\"STOP\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getCompletionTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
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
		public Client genAiClient() {
			String apiKey = System.getenv("GOOGLE_API_KEY");
			return Client.builder().apiKey(apiKey).build();
		}

		@Bean
		public GoogleGenAiChatModel vertexAiEmbedding(Client genAiClient, TestObservationRegistry observationRegistry) {

			return GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.observationRegistry(observationRegistry)
				.defaultOptions(GoogleGenAiChatOptions.builder()
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_PRO_PREVIEW)
					.build())
				.build();
		}

	}

}
