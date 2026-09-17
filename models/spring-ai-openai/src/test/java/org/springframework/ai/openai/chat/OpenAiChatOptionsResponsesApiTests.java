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

package org.springframework.ai.openai.chat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.Api;
import org.springframework.ai.openai.responses.ServersideTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The Responses API side of {@link OpenAiChatOptions}: the settings only that endpoint
 * has, and the rule that decides which endpoint a request goes to.
 *
 * @author Dimitar Proynov
 */
class OpenAiChatOptionsResponsesApiTests {

	@Test
	void chatCompletionsIsTheDefaultEndpoint() {
		var options = OpenAiChatOptions.builder().build();

		assertThat(options.getApi()).isEqualTo(Api.AUTO);
		assertThat(options.getModel()).isEqualTo(OpenAiChatOptions.DEFAULT_CHAT_MODEL);
		assertThat(options.resolveApi()).isEqualTo(Api.CHAT_COMPLETIONS);
	}

	/**
	 * GPT-5.4 is where Chat Completions stopped serving reasoning together with tool
	 * calling, so it is where the default flips.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "gpt-5.4", "gpt-5.4-mini", "gpt-5.4-nano-2026-03-17", "gpt-5.6-luna", "gpt-5.10",
			"gpt-6-mini", "gpt-12" })
	void recentModelsGoToTheResponsesApi(String model) {
		assertThat(OpenAiChatOptions.builder().model(model).build().resolveApi()).isEqualTo(Api.RESPONSES);
	}

	/**
	 * Everything unrecognized stays on Chat Completions, which is the endpoint every
	 * OpenAI-compatible provider implements. A Microsoft Foundry deployment name is
	 * arbitrary, so it lands here too.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "gpt-4o", "gpt-4.1-mini", "gpt-5", "gpt-5-mini", "gpt-5.1", "gpt-5.3-chat-latest", "o3",
			"o4-mini", "chatgpt-4o-latest", "my-prod-deployment", "deepseek-chat" })
	void everythingElseStaysOnChatCompletions(String model) {
		assertThat(OpenAiChatOptions.builder().model(model).build().resolveApi()).isEqualTo(Api.CHAT_COMPLETIONS);
	}

	@Test
	void anExplicitEndpointWinsOverTheModel() {
		assertThat(OpenAiChatOptions.builder().model("gpt-5.6-luna").api(Api.CHAT_COMPLETIONS).build().resolveApi())
			.isEqualTo(Api.CHAT_COMPLETIONS);
		assertThat(OpenAiChatOptions.builder().model("gpt-4o").api(Api.RESPONSES).build().resolveApi())
			.isEqualTo(Api.RESPONSES);
	}

	/**
	 * Configuring something only the Responses API provides is as good as asking for it:
	 * the alternative is a request that quietly does less than it was told to.
	 */
	@Test
	void aResponsesOnlySettingSelectsTheResponsesApi() {
		assertThat(responsesOnly(OpenAiChatOptions.builder().reasoningSummary("auto"))).isTrue();
		assertThat(responsesOnly(OpenAiChatOptions.builder().serversideTools(ServersideTool.WebSearch.of()))).isTrue();
		assertThat(responsesOnly(OpenAiChatOptions.builder().include(List.of("web_search_call.results")))).isTrue();
		assertThat(responsesOnly(OpenAiChatOptions.builder().maxToolCalls(3))).isTrue();
		assertThat(responsesOnly(OpenAiChatOptions.builder().truncation("auto"))).isTrue();
		// settings both endpoints have change nothing
		assertThat(responsesOnly(OpenAiChatOptions.builder().reasoningEffort("high"))).isFalse();
		assertThat(responsesOnly(OpenAiChatOptions.builder().safetyIdentifier("user-1"))).isFalse();
	}

	@Test
	void gitHubModelsCannotBeAskedForTheResponsesApi() {
		var options = OpenAiChatOptions.builder().gitHubModels(true).api(Api.RESPONSES).build();

		assertThatIllegalStateException().isThrownBy(options::resolveApi).withMessageContaining("GitHub Models");
	}

	@Test
	void gitHubModelsStaysOnChatCompletionsUnderAuto() {
		var options = OpenAiChatOptions.builder().gitHubModels(true).model("gpt-5.6-luna").build();

		assertThat(options.resolveApi()).isEqualTo(Api.CHAT_COMPLETIONS);
	}

	/**
	 * {@code max_output_tokens} is the Responses name for the cap Chat Completions calls
	 * {@code max_completion_tokens}: one number, three spellings, and the deprecated
	 * {@code max_tokens} as the fallback.
	 */
	@Test
	void maxOutputTokensIsTheCompletionTokenCap() {
		assertThat(OpenAiChatOptions.builder().maxOutputTokens(512).build().getMaxCompletionTokens()).isEqualTo(512);
		assertThat(OpenAiChatOptions.builder().maxCompletionTokens(512).build().getMaxOutputTokens()).isEqualTo(512);
		assertThat(OpenAiChatOptions.builder().maxTokens(256).build().getMaxOutputTokens()).isEqualTo(256);
		assertThat(OpenAiChatOptions.builder().build().getMaxOutputTokens()).isNull();
	}

	@Test
	void mutatePreservesEveryResponsesSetting() {
		var options = OpenAiChatOptions.builder()
			.api(Api.RESPONSES)
			.model("gpt-5-mini")
			.baseUrl("https://example.com")
			.apiKey("secret")
			.deploymentName("deployment")
			.timeout(Duration.ofSeconds(42))
			.maxRetries(1)
			.maxOutputTokens(256)
			.temperature(0.3)
			.topP(0.9)
			.topLogprobs(3)
			.reasoningEffort("high")
			.reasoningSummary("auto")
			.verbosity("low")
			.strict(true)
			.toolChoice("required")
			.parallelToolCalls(false)
			.maxToolCalls(4)
			.serversideTools(ServersideTool.WebSearch.of())
			.include(List.of("web_search_call.results"))
			.truncation("auto")
			.serviceTier("flex")
			.promptCacheKey("cache")
			.safetyIdentifier("user-1")
			.metadata(Map.of("tenant", "acme"))
			.extraBody(Map.of("knob", "on"))
			.build();

		assertThat(options.mutate().build()).isEqualTo(options);
		assertThat(options.mutate().build().getApi()).isEqualTo(Api.RESPONSES);
		assertThat(options.mutate().build().getTimeout()).isEqualTo(Duration.ofSeconds(42));
		assertThat(options.mutate().build().getBaseUrl()).isEqualTo("https://example.com");
		assertThat(options.mutate().build().getServersideTools()).containsExactly(ServersideTool.WebSearch.of());
	}

	@Test
	void runtimeOptionsOverrideTheDefaultsTheyMentionAndLeaveTheRestAlone() {
		var defaults = OpenAiChatOptions.builder()
			.api(Api.RESPONSES)
			.model("gpt-5-mini")
			.reasoningEffort("high")
			.temperature(0.1)
			.build();

		OpenAiChatOptions.Builder merged = defaults.mutate();
		merged.combineWith(ChatOptions.builder().temperature(0.9).build().mutate());

		assertThat(merged.build().getTemperature()).isEqualTo(0.9);
		assertThat(merged.build().getReasoningEffort()).isEqualTo("high");
		assertThat(merged.build().getModel()).isEqualTo("gpt-5-mini");
		assertThat(merged.build().getApi()).isEqualTo(Api.RESPONSES);
	}

	/**
	 * Every request asks for {@code reasoning.encrypted_content}; a runtime request
	 * asking for one more field must not drop what the bean was configured with.
	 */
	@Test
	void includeFieldsAreUnionedWhenMerging() {
		var defaults = OpenAiChatOptions.builder().include(List.of("web_search_call.results")).build();
		var runtime = OpenAiChatOptions.builder().include(List.of("file_search_call.results")).build();

		OpenAiChatOptions.Builder merged = defaults.mutate();
		merged.combineWith(runtime.mutate());

		assertThat(merged.build().getInclude()).containsExactly("web_search_call.results", "file_search_call.results");
	}

	/**
	 * Options a request's endpoint has no equivalent for are kept rather than dropped:
	 * the same options bean is routinely used against both endpoints, and the other one
	 * honours them. They are warned about, once per option, when the request is built.
	 */
	@Test
	void chatCompletionsOnlySettingsSurviveOnAResponsesRequest() {
		var options = OpenAiChatOptions.builder()
			.api(Api.RESPONSES)
			.stopSequences(List.of("END"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.seed(42)
			.build();

		assertThat(options.getStopSequences()).containsExactly("END");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(options.getSeed()).isEqualTo(42);
	}

	@Test
	void equalityCoversTheResponsesSettings() {
		var first = OpenAiChatOptions.builder().model("gpt-5-mini").reasoningSummary("auto").build();
		var second = OpenAiChatOptions.builder().model("gpt-5-mini").reasoningSummary("detailed").build();

		assertThat(first).isNotEqualTo(second);
		assertThat(first).isEqualTo(OpenAiChatOptions.builder().model("gpt-5-mini").reasoningSummary("auto").build());
		assertThat(OpenAiChatOptions.builder().api(Api.RESPONSES).build())
			.isNotEqualTo(OpenAiChatOptions.builder().api(Api.CHAT_COMPLETIONS).build());
	}

	/**
	 * A model old enough to stay on Chat Completions, so the only thing that can move the
	 * request is the setting under test.
	 */
	private static boolean responsesOnly(OpenAiChatOptions.Builder builder) {
		return builder.model("gpt-4o").build().resolveApi() == Api.RESPONSES;
	}

}
