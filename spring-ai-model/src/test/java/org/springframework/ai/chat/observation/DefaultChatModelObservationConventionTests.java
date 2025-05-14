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

package org.springframework.ai.chat.observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Unit tests for {@link DefaultChatModelObservationConvention}.
 *
 * @author Thomas Vitale
 * @author Alexandros Pappas
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
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("chat mistral");
	}

	@Test
	void contextualNameWhenModelIsNotDefined() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("chat");
	}

	@Test
	void supportsOnlyChatModelObservationContext() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveLowCardinalityKeyValuesWhenDefined() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), "chat"),
				KeyValue.of(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider"),
				KeyValue.of(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "mistral"));
	}

	@Test
	void shouldHaveKeyValuesWhenDefinedAndResponse() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder()
				.model("mistral")
				.frequencyPenalty(0.8)
				.maxTokens(200)
				.presencePenalty(1.0)
				.stopSequences(List.of("addio", "bye"))
				.temperature(0.5)
				.topK(1)
				.topP(0.9)
				.build()))
			.provider("superprovider")
			.build();
		observationContext.setResponse(new ChatResponse(
				List.of(new Generation(new AssistantMessage("response"),
						ChatGenerationMetadata.builder().finishReason("this-is-the-end").build())),
				ChatResponseMetadata.builder().id("say33").model("mistral-42").usage(new TestUsage()).build()));
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
	void shouldNotHaveKeyValuesWhenMissing() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext))
			.contains(KeyValue.of(LowCardinalityKeyNames.REQUEST_MODEL.asString(), KeyValue.NONE_VALUE))
			.contains(KeyValue.of(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), KeyValue.NONE_VALUE));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)
			.stream()
			.map(KeyValue::getKey)
			.toList()).doesNotContain(HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(),
					HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(),
					HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(),
					HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(),
					HighCardinalityKeyNames.REQUEST_TOOL_NAMES.asString(),
					HighCardinalityKeyNames.REQUEST_TOP_K.asString(), HighCardinalityKeyNames.REQUEST_TOP_P.asString(),
					HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					HighCardinalityKeyNames.RESPONSE_ID.asString(),
					HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString());
	}

	@Test
	void shouldNotHaveKeyValuesWhenEmptyValues() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().stopSequences(List.of()).build()))
			.provider("superprovider")
			.build();
		observationContext.setResponse(new ChatResponse(
				List.of(new Generation(new AssistantMessage("response"),
						ChatGenerationMetadata.builder().finishReason("").build())),
				ChatResponseMetadata.builder().id("").build()));
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)
			.stream()
			.map(KeyValue::getKey)
			.toList()).doesNotContain(HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					HighCardinalityKeyNames.RESPONSE_ID.asString());
	}

	@Test
	void shouldHaveKeyValuesWhenTools() {
		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ToolCallingChatOptions.builder()
				.model("mistral")
				.toolNames("toolA", "toolB")
				.toolCallbacks(new TestToolCallback("tool1", true), new TestToolCallback("tool2", false),
						new TestToolCallback("toolB"))
				.build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).anySatisfy(keyValue -> {
			assertThat(keyValue.getKey()).isEqualTo(HighCardinalityKeyNames.REQUEST_TOOL_NAMES.asString());
			assertThat(keyValue.getValue()).contains("toolA", "toolB", "tool1", "tool2");
		});
	}

	private Prompt generatePrompt(ChatOptions chatOptions) {
		return new Prompt("Who let the dogs out?", chatOptions);
	}

	static class TestUsage implements Usage {

		@Override
		public Integer getPromptTokens() {
			return 1000;
		}

		@Override
		public Integer getCompletionTokens() {
			return 500;
		}

		@Override
		public Map<String, Integer> getNativeUsage() {
			Map<String, Integer> usage = new HashMap<>();
			usage.put("promptTokens", getPromptTokens());
			usage.put("completionTokens", getCompletionTokens());
			usage.put("totalTokens", getTotalTokens());
			return usage;
		}

	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
