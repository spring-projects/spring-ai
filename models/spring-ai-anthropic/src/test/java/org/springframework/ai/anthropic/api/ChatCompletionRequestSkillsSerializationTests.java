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

package org.springframework.ai.anthropic.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicSkill;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicApi.Skill;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillContainer;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatCompletionRequest} serialization with Skills.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class ChatCompletionRequestSkillsSerializationTests {

	@Test
	void shouldSerializeRequestWithSkills() {
		SkillContainer container = SkillContainer.builder().skill(AnthropicSkill.XLSX).build();

		AnthropicMessage message = new AnthropicMessage(List.of(new ContentBlock("Create a spreadsheet")), Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model("claude-sonnet-4-5")
			.messages(List.of(message))
			.maxTokens(1024)
			.container(container)
			.build();

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).contains("\"container\"");
		assertThat(json).contains("\"skills\"");
		assertThat(json).contains("\"type\":\"anthropic\"");
		assertThat(json).contains("\"skill_id\":\"xlsx\"");
		assertThat(json).contains("\"version\":\"latest\"");
	}

	@Test
	void shouldSerializeMultipleSkills() throws Exception {
		SkillContainer container = SkillContainer.builder()
			.skill(AnthropicSkill.XLSX)
			.skill(AnthropicSkill.PPTX, "20251013")
			.skill("custom-skill")
			.build();

		AnthropicMessage message = new AnthropicMessage(List.of(new ContentBlock("Create documents")), Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model("claude-sonnet-4-5")
			.messages(List.of(message))
			.maxTokens(1024)
			.container(container)
			.build();

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).contains("\"xlsx\"");
		assertThat(json).contains("\"pptx\"");
		assertThat(json).contains("\"custom-skill\"");
		assertThat(json).contains("\"20251013\"");
	}

	@Test
	void shouldNotIncludeContainerWhenNull() throws Exception {
		AnthropicMessage message = new AnthropicMessage(List.of(new ContentBlock("Simple message")), Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model("claude-sonnet-4-5")
			.messages(List.of(message))
			.maxTokens(1024)
			.build();

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).doesNotContain("\"container\"");
	}

	@Test
	void shouldSerializeRequestWithSkillsUsingBuilderSkillsMethod() throws Exception {
		List<Skill> skills = List.of(new Skill(SkillType.ANTHROPIC, "docx", "latest"),
				new Skill(SkillType.CUSTOM, "my-skill", "20251013"));

		AnthropicMessage message = new AnthropicMessage(List.of(new ContentBlock("Create documents")), Role.USER);

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.model("claude-sonnet-4-5")
			.messages(List.of(message))
			.maxTokens(1024)
			.skills(skills)
			.build();

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).contains("\"container\"");
		assertThat(json).contains("\"skills\"");
		assertThat(json).contains("\"docx\"");
		assertThat(json).contains("\"my-skill\"");
		assertThat(json).contains("\"20251013\"");
	}

	@Test
	void shouldDeserializeRequestWithSkills() throws Exception {
		String json = """
				{
					"model": "claude-sonnet-4-5",
					"messages": [
						{
							"role": "user",
							"content": [{"type": "text", "text": "Hello"}]
						}
					],
					"max_tokens": 1024,
					"container": {
						"skills": [
							{
								"type": "anthropic",
								"skill_id": "xlsx",
								"version": "latest"
							}
						]
					}
				}
				""";

		ChatCompletionRequest request = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		assertThat(request.container()).isNotNull();
		assertThat(request.container().skills()).hasSize(1);
		assertThat(request.container().skills().get(0).type()).isEqualTo(SkillType.ANTHROPIC);
		assertThat(request.container().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(request.container().skills().get(0).version()).isEqualTo("latest");
	}

}
