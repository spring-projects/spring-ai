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

import java.util.List;
import java.util.Map;

import com.openai.core.ObjectMappers;
import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Asserts the serialized request body, since that is what OpenAI actually sees.
 *
 * @author Dimitar Proynov
 */
class ResponsesRequestBuilderTests {

	private static final ToolCallingManager NO_TOOLS = toolCallingManager(List.of());

	private static ToolCallingManager toolCallingManager(List<ToolDefinition> toolDefinitions) {
		ToolCallingManager manager = mock(ToolCallingManager.class);
		when(manager.resolveToolDefinitions(any())).thenReturn(toolDefinitions);
		return manager;
	}

	private static String body(OpenAiResponsesChatOptions options, ToolCallingManager toolCallingManager) {
		ResponseCreateParams params = ResponsesRequestBuilder.build(new Prompt(List.of(new UserMessage("Hi")), options),
				toolCallingManager);
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(params._body());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String body(OpenAiResponsesChatOptions options) {
		return body(options, NO_TOOLS);
	}

	/**
	 * Not a preference: without the encrypted reasoning blob coming back, a reasoning
	 * turn cannot be replayed at all.
	 */
	@Test
	void encryptedReasoningContentIsAlwaysRequested() {
		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build()))
			.contains("\"include\":[\"reasoning.encrypted_content\"]");
	}

	@Test
	void configuredIncludeFieldsAreUnionedRatherThanReplacingTheEncryptedReasoningRequest() {
		String body = body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.include(List.of("web_search_call.results"))
			.build());

		assertThat(body).contains("reasoning.encrypted_content").contains("web_search_call.results");
	}

	/**
	 * Not a preference either, and not configurable: a stored response keeps its
	 * reasoning at OpenAI rather than returning it, so storing it would make the
	 * stateless replay this model is built on impossible.
	 */
	@Test
	void storeIsAlwaysSentAsFalse() {
		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())).contains("\"store\":false");
	}

	@Test
	void maxTokensBecomesMaxOutputTokens() {
		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").maxTokens(256).build()))
			.contains("\"max_output_tokens\":256")
			.doesNotContain("max_tokens");
	}

	@Test
	void reasoningEffortAndSummaryGoUnderReasoning() {
		assertThat(body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.reasoningEffort("HIGH")
			.reasoningSummary("Auto")
			.build())).contains("\"reasoning\":{\"effort\":\"high\",\"summary\":\"auto\"}");
	}

	@Test
	void verbosityAndStructuredOutputGoUnderText() {
		String body = body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.verbosity("low")
			.outputSchema("{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}}}")
			.build());

		assertThat(body).contains("\"verbosity\":\"low\"")
			.contains("\"type\":\"json_schema\"")
			.contains("\"answer\"")
			.doesNotContain("response_format");
	}

	/**
	 * The Responses API attempts strict mode when the flag is omitted, and Spring AI's
	 * generated schemas frequently do not satisfy it, so the flag is always sent.
	 */
	@Test
	void functionToolsAreSentAsNonStrictUnlessTheCallerOptsIn() {
		ToolCallingManager tools = toolCallingManager(List.of(ToolDefinition.builder()
			.name("getCurrentWeather")
			.description("Get the weather")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}")
			.build()));

		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build(), tools))
			.contains("\"strict\":false")
			.contains("\"name\":\"getCurrentWeather\"")
			.contains("\"location\"");
		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").strict(true).build(), tools))
			.contains("\"strict\":true");
	}

	/**
	 * A tool sent with empty parameters looks usable to the model, which then calls it
	 * with {} and fails somewhere far less obvious.
	 */
	@Test
	void anUnparseableToolSchemaFailsTheRequest() {
		ToolCallingManager tools = toolCallingManager(List.of(ToolDefinition.builder()
			.name("getCurrentWeather")
			.description("Get the weather")
			.inputSchema("{ this is not json")
			.build()));

		assertThatThrownBy(() -> body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build(), tools))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("getCurrentWeather");
	}

	@Test
	void hostedToolsAreAddedAlongsideFunctionTools() {
		String body = body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.hostedTools(new HostedTool.WebSearch("high", List.of("spring.io")), HostedTool.FileSearch.of("vs_123"),
					HostedTool.CodeInterpreter.of(), new HostedTool.Raw(Map.of("type", "local_shell")))
			.build());

		assertThat(body).contains("\"type\":\"web_search\"")
			.contains("\"search_context_size\":\"high\"")
			.contains("spring.io")
			.contains("\"type\":\"file_search\"")
			.contains("vs_123")
			.contains("\"type\":\"code_interpreter\"")
			.contains("\"type\":\"local_shell\"");
	}

	@Test
	void toolChoiceAcceptsKeywordsFunctionNamesAndHostedToolTypes() {
		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").toolChoice("required").build()))
			.contains("\"tool_choice\":\"required\"");
		assertThat(body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.toolChoice("{\"type\":\"function\",\"name\":\"getCurrentWeather\"}")
			.build())).contains("\"tool_choice\":{\"name\":\"getCurrentWeather\",\"type\":\"function\"}");
		assertThat(body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.toolChoice("{\"type\":\"file_search\"}")
			.build())).contains("\"tool_choice\":{\"type\":\"file_search\"}");
	}

	@Test
	void extraBodyIsPassedThroughForOpenAiCompatibleProviders() {
		assertThat(body(OpenAiResponsesChatOptions.builder()
			.model("gpt-5-mini")
			.extraBody(Map.of("custom_knob", "on"))
			.build())).contains("\"custom_knob\":\"on\"");
	}

	@Test
	void microsoftFoundryDeploymentNameWinsOverTheModelName() {
		assertThat(
				body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").deploymentName("my-deployment").build()))
			.contains("\"model\":\"my-deployment\"");
	}

	@Test
	void responseFormatTypesOtherThanJsonSchemaAreSupported() {
		var jsonObject = OpenAiChatModel.ResponseFormat.builder()
			.type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
			.build();

		assertThat(body(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").responseFormat(jsonObject).build()))
			.contains("\"type\":\"json_object\"");
	}

}
