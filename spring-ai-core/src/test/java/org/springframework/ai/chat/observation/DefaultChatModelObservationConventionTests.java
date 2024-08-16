/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.chat.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Unit tests for {@link DefaultChatModelObservationConvention}.
 *
 * @author Thomas Vitale
 */
class DefaultChatModelObservationConventionTests {

	private final DefaultChatModelObservationConvention observationConvention = new DefaultChatModelObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void contextualNameWhenModelIsDefined() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("chat mistral");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().build())
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("chat");
	}

	@Test
	void supportsOnlyChatModelObservationContext() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), "chat"),
				KeyValue.of(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider"),
				KeyValue.of(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "mistral"));
	}

	@Test
	void shouldHaveKeyValuesWhenDefinedAndResponse() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder()
				.withModel("mistral")
				.withFrequencyPenalty(0.8f)
				.withMaxTokens(200)
				.withPresencePenalty(1.0f)
				.withStopSequences(List.of("addio", "bye"))
				.withTemperature(0.5f)
				.withTopK(1)
				.withTopP(0.9f)
				.build())
			.build();
		observationContext.setResponse(new ChatResponse(
				List.of(new Generation(new AssistantMessage("response"),
						ChatGenerationMetadata.from("this-is-the-end", null))),
				ChatResponseMetadata.builder()
					.withId("say33")
					.withModel("mistral-42")
					.withUsage(new TestUsage())
					.build()));
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext))
			.contains(KeyValue.of(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), "mistral-42"));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(), "0.8"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "200"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(), "1.0"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(), "[\"addio\", \"bye\"]"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.5"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TOP_K.asString(), "1"),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "0.9"),
				KeyValue.of(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), "[\"this-is-the-end\"]"),
				KeyValue.of(HighCardinalityKeyNames.RESPONSE_ID.asString(), "say33"),
				KeyValue.of(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(), "1000"),
				KeyValue.of(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(), "500"),
				KeyValue.of(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(), "1500"));
	}

	@Test
	void shouldHaveNoneKeyValuesWhenMissing() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().build())
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.REQUEST_MODEL.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), KeyValue.NONE_VALUE));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TOP_K.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.REQUEST_TOP_P.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.RESPONSE_ID.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(), KeyValue.NONE_VALUE));
	}

	private Prompt generatePrompt() {
		return new Prompt("Who let the dogs out?");
	}

	static class TestUsage implements Usage {

		@Override
		public Long getPromptTokens() {
			return 1000L;
		}

		@Override
		public Long getGenerationTokens() {
			return 500L;
		}

	}

}
