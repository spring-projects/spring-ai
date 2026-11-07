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

package org.springframework.ai.deepseek;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.deepseek.DeepSeekChatOptions.Builder;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.MockWeatherService;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.test.options.AbstractChatOptionsTests;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DeepSeekChatOptions}.
 *
 * @author Geng Rong
 * @author guan xu
 */
class DeepSeekChatOptionsTests extends AbstractChatOptionsTests<DeepSeekChatOptions, Builder> {

	@Override
	protected Class<DeepSeekChatOptions> getConcreteOptionsClass() {
		return DeepSeekChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return DeepSeekChatOptions.builder().model(DeepSeekApi.DEFAULT_CHAT_MODEL);
	}

	@Test
	void testCombineWithCollections() {
		DeepSeekApi.FunctionTool baseTool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("base-function", "", "{}"));
		DeepSeekChatOptions base = DeepSeekChatOptions.builder().tools(java.util.List.of(baseTool)).build();

		DeepSeekApi.FunctionTool overrideTool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("override-function", "", "{}"));
		DeepSeekChatOptions override = DeepSeekChatOptions.builder().tools(java.util.List.of(overrideTool)).build();

		DeepSeekChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		org.assertj.core.api.Assertions.assertThat(merged.getTools()).containsExactlyInAnyOrder(baseTool, overrideTool);
	}

	@Test
	void cloneCreatesIndependentToolsList() {
		DeepSeekApi.FunctionTool tool = new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("function", "", "{}"));
		List<DeepSeekApi.FunctionTool> tools = new ArrayList<>();
		tools.add(tool);

		Builder source = DeepSeekChatOptions.builder().tools(tools);
		Builder clone = source.clone();
		tools.add(new DeepSeekApi.FunctionTool(DeepSeekApi.FunctionTool.Type.FUNCTION,
				new DeepSeekApi.FunctionTool.Function("other", "", "{}")));

		assertThat(clone.build().getTools()).containsExactly(tool);
	}

	@Test
	void cloneHandlesNullToolsList() {
		assertThat(DeepSeekChatOptions.builder().clone().build().getTools()).isNull();
	}

	@Test
	void testGetters() {
		DeepSeekChatOptions options = fullyPopulatedBuilder().build();

		assertThat(options.getModel()).isEqualTo("deepseek-chat");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(128);
		assertThat(options.getPresencePenalty()).isEqualTo(0.3);
		assertThat(options.getResponseFormat())
			.isEqualTo(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
		assertThat(options.getStop()).containsExactly("foo", "bar");
		assertThat(options.getStopSequences()).containsExactly("foo", "bar");
		assertThat(options.getTemperature()).isEqualTo(0.2);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(5);
		assertThat(options.getEcho()).isTrue();
		assertThat(options.getSuffix()).isEqualTo("return result");
		assertThat(options.getTools()).hasSize(1);
		assertThat(options.getToolChoice()).isEqualTo("auto");
		assertThat(options.getToolCallbacks()).hasSize(1);
		assertThat(options.getToolContext()).containsEntry("locale", "en-US");
		assertThat(options.getTopK()).isNull();
	}

	@Test
	void testMutate() {
		DeepSeekChatOptions options = fullyPopulatedBuilder().build();
		DeepSeekChatOptions mutated = options.mutate().build();

		assertThat(mutated).isEqualTo(options);
		assertThat(mutated).isNotSameAs(options);
		assertThat(mutated.getModel()).isEqualTo(options.getModel());
		assertThat(mutated.getFrequencyPenalty()).isEqualTo(options.getFrequencyPenalty());
		assertThat(mutated.getMaxTokens()).isEqualTo(options.getMaxTokens());
		assertThat(mutated.getPresencePenalty()).isEqualTo(options.getPresencePenalty());
		assertThat(mutated.getResponseFormat()).isEqualTo(options.getResponseFormat());
		assertThat(mutated.getStop()).isEqualTo(options.getStop());
		assertThat(mutated.getTemperature()).isEqualTo(options.getTemperature());
		assertThat(mutated.getTopP()).isEqualTo(options.getTopP());
		assertThat(mutated.getLogprobs()).isEqualTo(options.getLogprobs());
		assertThat(mutated.getTopLogprobs()).isEqualTo(options.getTopLogprobs());
		assertThat(mutated.getEcho()).isEqualTo(options.getEcho());
		assertThat(mutated.getSuffix()).isEqualTo(options.getSuffix());
		assertThat(mutated.getTools()).isEqualTo(options.getTools());
		assertThat(mutated.getToolChoice()).isEqualTo(options.getToolChoice());
		assertThat(mutated.getToolCallbacks()).isEqualTo(options.getToolCallbacks());
		assertThat(mutated.getToolContext()).isEqualTo(options.getToolContext());
	}

	@Test
	void combineWithOverridesScalars() {
		DeepSeekChatOptions base = fullyPopulatedBuilder().build();
		DeepSeekChatOptions override = DeepSeekChatOptions.builder()
			.model("deepseek-reasoner")
			.frequencyPenalty(1.0)
			.maxTokens(256)
			.presencePenalty(0.8)
			.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build())
			.stop(List.of("baz"))
			.temperature(0.9)
			.topP(0.1)
			.logprobs(false)
			.topLogprobs(2)
			.echo(false)
			.suffix("other suffix")
			.toolChoice("none")
			.build();

		DeepSeekChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getModel()).isEqualTo("deepseek-reasoner");
		assertThat(merged.getFrequencyPenalty()).isEqualTo(1.0);
		assertThat(merged.getMaxTokens()).isEqualTo(256);
		assertThat(merged.getPresencePenalty()).isEqualTo(0.8);
		assertThat(merged.getResponseFormat())
			.isEqualTo(ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build());
		assertThat(merged.getTemperature()).isEqualTo(0.9);
		assertThat(merged.getTopP()).isEqualTo(0.1);
		assertThat(merged.getLogprobs()).isFalse();
		assertThat(merged.getTopLogprobs()).isEqualTo(2);
		assertThat(merged.getEcho()).isFalse();
		assertThat(merged.getSuffix()).isEqualTo("other suffix");
		assertThat(merged.getToolChoice()).isEqualTo("none");
		assertThat(merged.getStop()).containsExactly("foo", "bar", "baz");
	}

	@Test
	void combineWithKeepsBaseValuesWhenOverrideIsNull() {
		DeepSeekChatOptions base = fullyPopulatedBuilder().build();
		DeepSeekChatOptions override = DeepSeekChatOptions.builder().build();

		DeepSeekChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(merged.getMaxTokens()).isEqualTo(128);
		assertThat(merged.getPresencePenalty()).isEqualTo(0.3);
		assertThat(merged.getTemperature()).isEqualTo(0.2);
		assertThat(merged.getTopP()).isEqualTo(0.9);
		assertThat(merged.getLogprobs()).isTrue();
		assertThat(merged.getTopLogprobs()).isEqualTo(5);
		assertThat(merged.getEcho()).isTrue();
		assertThat(merged.getSuffix()).isEqualTo("return result");
		assertThat(merged.getResponseFormat())
			.isEqualTo(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
		assertThat(merged.getToolChoice()).isEqualTo("auto");
	}

	@Test
	void testEqualsAndHashCode() {
		DeepSeekChatOptions a = fullyPopulatedBuilder().build();
		DeepSeekChatOptions b = fullyPopulatedBuilder().build();

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}

	private Builder fullyPopulatedBuilder() {
		return DeepSeekChatOptions.builder()
			.model("deepseek-chat")
			.frequencyPenalty(0.5)
			.maxTokens(128)
			.presencePenalty(0.3)
			.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
			.stop(List.of("foo", "bar"))
			.temperature(0.2)
			.topP(0.9)
			.logprobs(true)
			.topLogprobs(5)
			.echo(true)
			.suffix("return result")
			.tools(List.of(FUNCTION_TOOL))
			.toolChoice("auto")
			.toolCallbacks(List.of(WEATHER_TOOL_CALLBACK))
			.toolContext(Map.of("locale", "en-US"));
	}

	private static final DeepSeekApi.FunctionTool FUNCTION_TOOL = new DeepSeekApi.FunctionTool(
			DeepSeekApi.FunctionTool.Type.FUNCTION, new DeepSeekApi.FunctionTool.Function("function", "desc", "{}"));

	private static final FunctionToolCallback<MockWeatherService.Request, MockWeatherService.Response> WEATHER_TOOL_CALLBACK = FunctionToolCallback
		.builder("getCurrentWeather", new MockWeatherService())
		.description("Get the weather in location")
		.inputType(MockWeatherService.Request.class)
		.build();

}
