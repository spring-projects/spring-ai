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

package org.springframework.ai.openai.responses;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dimitar Proynov
 */
class OpenAiResponsesChatOptionsTests {

	@Test
	void portableMaxTokensAndMaxOutputTokensAreTheSameSetting() {
		assertThat(OpenAiResponsesChatOptions.builder().maxTokens(512).build().getMaxOutputTokens()).isEqualTo(512);
		assertThat(OpenAiResponsesChatOptions.builder().maxOutputTokens(512).build().getMaxTokens()).isEqualTo(512);
	}

	@Test
	void modelFallsBackToTheDefaultWhenUnset() {
		assertThat(OpenAiResponsesChatOptions.builder().build().getModel())
			.isEqualTo(OpenAiResponsesChatOptions.DEFAULT_CHAT_MODEL);
	}

	/**
	 * These have no Responses equivalent, so they are dropped rather than silently
	 * mistranslated. The model warns once per option.
	 */
	@Test
	void portableOptionsWithoutAResponsesEquivalentReadBackAsNull() {
		var options = OpenAiResponsesChatOptions.builder()
			.stopSequences(List.of("END"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.topK(10)
			.build();

		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getTopK()).isNull();
	}

	@Test
	void outputSchemaRoundTripsThroughTheResponseFormat() {
		String schema = "{\"type\":\"object\"}";

		var options = OpenAiResponsesChatOptions.builder().outputSchema(schema).build();

		assertThat(options.getOutputSchema()).isEqualTo(schema);
		assertThat(options.getResponseFormat()).isNotNull();
	}

	@Test
	void mutatePreservesEverySetting() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.baseUrl("https://example.com")
			.apiKey("secret")
			.deploymentName("deployment")
			.timeout(Duration.ofSeconds(42))
			.maxRetries(1)
			.maxOutputTokens(256)
			.temperature(0.3)
			.topP(0.9)
			.reasoningEffort("high")
			.reasoningSummary("auto")
			.verbosity("low")
			.strict(true)
			.toolChoice("required")
			.parallelToolCalls(false)
			.maxToolCalls(4)
			.hostedTools(HostedTool.WebSearch.of())
			.include(List.of("web_search_call.results"))
			.truncation("auto")
			.serviceTier("flex")
			.promptCacheKey("cache")
			.safetyIdentifier("user-1")
			.metadata(Map.of("tenant", "acme"))
			.extraBody(Map.of("knob", "on"))
			.build();

		assertThat(options.mutate().build()).isEqualTo(options);
		assertThat(options.mutate().build().getTimeout()).isEqualTo(Duration.ofSeconds(42));
		assertThat(options.mutate().build().getBaseUrl()).isEqualTo("https://example.com");
		assertThat(options.mutate().build().getHostedTools()).containsExactly(HostedTool.WebSearch.of());
	}

	@Test
	void runtimeOptionsOverrideTheDefaultsTheyMentionAndLeaveTheRestAlone() {
		var defaults = OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.reasoningEffort("high")
			.temperature(0.1)
			.build();

		OpenAiResponsesChatOptions.Builder merged = defaults.mutate();
		merged.combineWith(ChatOptions.builder().temperature(0.9).build().mutate());

		assertThat(merged.build().getTemperature()).isEqualTo(0.9);
		assertThat(merged.build().getReasoningEffort()).isEqualTo("high");
		assertThat(merged.build().getModel()).isEqualTo("gpt-5-mini");
	}

	/**
	 * The model always requests {@code reasoning.encrypted_content}; a runtime request
	 * asking for one more field must not drop what the bean was configured with.
	 */
	@Test
	void includeFieldsAreUnionedWhenMerging() {
		var defaults = OpenAiResponsesChatOptions.builder().include(List.of("web_search_call.results")).build();
		var runtime = OpenAiResponsesChatOptions.builder().include(List.of("file_search_call.results")).build();

		OpenAiResponsesChatOptions.Builder merged = defaults.mutate();
		merged.combineWith(runtime.mutate());

		assertThat(merged.build().getInclude()).containsExactly("web_search_call.results", "file_search_call.results");
	}

	/**
	 * This is the path {@code ChatClient} takes when applying its defaults, and it
	 * bypasses the builder setters, so the unsupported options have to be dropped on
	 * merge too.
	 */
	@Test
	void unsupportedPortableOptionsAreAlsoDroppedWhenTheyArriveThroughAMerge() {
		OpenAiResponsesChatOptions.Builder merged = OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.build()
			.mutate();
		merged.combineWith(ChatOptions.builder()
			.stopSequences(List.of("END"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.topK(10)
			.temperature(0.7)
			.build()
			.mutate());

		var options = merged.build();

		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getTopK()).isNull();
		// the supported one still comes through
		assertThat(options.getTemperature()).isEqualTo(0.7);
	}

	@Test
	void toolCallingAndStructuredOutputContractsAreImplemented() {
		var options = OpenAiResponsesChatOptions.builder().build();

		assertThat(options).isInstanceOf(ToolCallingChatOptions.class);
		assertThat(options.mutate()).isInstanceOf(ToolCallingChatOptions.Builder.class);
	}

	@Test
	void equalityIgnoresConnectionSettingsButCoversGenerationSettings() {
		var first = OpenAiResponsesChatOptions.builder().model("gpt-5-mini").reasoningEffort("low").build();
		var second = OpenAiResponsesChatOptions.builder().model("gpt-5-mini").reasoningEffort("high").build();

		assertThat(first).isNotEqualTo(second);
		assertThat(first)
			.isEqualTo(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").reasoningEffort("low").build());
	}

}
