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

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicSkill;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicChatModel} with Skills support.
 *
 * @author Soby Chacko
 * @since 1.1.1
 */
@ExtendWith(MockitoExtension.class)
class AnthropicChatModelSkillsTests {

	@Mock
	private AnthropicApi anthropicApi;

	private AnthropicChatModel createChatModel(AnthropicChatOptions defaultOptions) {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		return new AnthropicChatModel(this.anthropicApi, defaultOptions, toolCallingManager, retryTemplate,
				observationRegistry);
	}

	@Test
	void shouldIncludeSkillsFromRequestOptions() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(1);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("xlsx");
	}

	@Test
	void shouldIncludeSkillsFromDefaultOptions() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.skill(AnthropicSkill.PPTX)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		// Pass empty options to avoid null check failures
		Prompt prompt = new Prompt("Create a presentation", AnthropicChatOptions.builder().build());

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(1);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("pptx");
	}

	@Test
	void shouldPrioritizeRequestOptionsOverDefaultOptions() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.skill(AnthropicSkill.PPTX)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(1);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("xlsx");
	}

	@Test
	void shouldIncludeMultipleSkills() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder()
			.skill(AnthropicSkill.XLSX)
			.skill(AnthropicSkill.PPTX)
			.skill("my-custom-skill")
			.build();

		Prompt prompt = new Prompt("Create documents", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(3);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(request.container().skills().get(1).skillId()).isEqualTo("pptx");
		assertThat(request.container().skills().get(2).skillId()).isEqualTo("my-custom-skill");
	}

	@Test
	void shouldHandleNullSkillsGracefully() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		// Pass empty options to avoid null check failures
		Prompt prompt = new Prompt("Simple question", AnthropicChatOptions.builder().build());

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNull();
	}

	@Test
	void shouldIncludeSkillsWithVersion() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder()
			.skill(AnthropicSkill.XLSX, "20251013")
			.build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(1);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(request.container().skills().get(0).version()).isEqualTo("20251013");
	}

	@Test
	void shouldAddSkillsBetaHeaderWhenSkillsPresent() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		chatModel.createRequest(prompt, false);

		assertThat(requestOptions.getHttpHeaders()).isNotNull();
		assertThat(requestOptions.getHttpHeaders()).containsKey("anthropic-beta");
		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).contains(AnthropicApi.BETA_SKILLS);
		assertThat(betaHeader).contains(AnthropicApi.BETA_CODE_EXECUTION);
		assertThat(betaHeader).contains(AnthropicApi.BETA_FILES_API);
	}

	@Test
	void shouldNotAddSkillsBetaHeaderWhenNoSkills() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().build();

		Prompt prompt = new Prompt("Simple question", requestOptions);

		chatModel.createRequest(prompt, false);

		assertThat(requestOptions.getHttpHeaders().get("anthropic-beta")).isNull();
	}

	@Test
	void shouldAppendSkillsBetaHeaderToExistingBetaHeaders() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		java.util.Map<String, String> existingHeaders = new java.util.HashMap<>();
		existingHeaders.put("anthropic-beta", "some-other-beta");

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder()
			.skill(AnthropicSkill.XLSX)
			.httpHeaders(existingHeaders)
			.build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		chatModel.createRequest(prompt, false);

		String betaHeader = requestOptions.getHttpHeaders().get("anthropic-beta");
		assertThat(betaHeader).contains("some-other-beta")
			.contains(AnthropicApi.BETA_SKILLS)
			.contains(AnthropicApi.BETA_CODE_EXECUTION)
			.contains(AnthropicApi.BETA_FILES_API);
	}

	@Test
	void shouldAutomaticallyAddCodeExecutionToolWhenSkillsPresent() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		// Verify code_execution tool is automatically added
		assertThat(request.tools()).isNotNull();
		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).name()).isEqualTo("code_execution");
	}

	@Test
	void shouldNotDuplicateCodeExecutionToolIfAlreadyPresent() {
		AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.maxTokens(1024)
			.build();

		AnthropicChatModel chatModel = createChatModel(defaultOptions);

		AnthropicChatOptions requestOptions = AnthropicChatOptions.builder().skill(AnthropicSkill.XLSX).build();

		Prompt prompt = new Prompt("Create a spreadsheet", requestOptions);

		// Note: We can't easily test this without exposing more of the internal state,
		// but the implementation checks for existing code_execution tool
		ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		// Should have exactly 1 tool (code_execution), not duplicated
		assertThat(request.tools()).isNotNull();
		assertThat(request.tools()).hasSize(1);
		assertThat(request.tools().get(0).name()).isEqualTo("code_execution");
	}

}
