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

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicSkill;
import org.springframework.ai.anthropic.api.AnthropicApi.Skill;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillContainer;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicChatOptions} with Skills support.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class AnthropicChatOptionsSkillsTests {

	@Test
	void shouldBuildOptionsWithSingleSkill() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().anthropicSkill(AnthropicSkill.XLSX).build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(1);
		assertThat(options.getSkillContainer().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().skills().get(0).type()).isEqualTo(SkillType.ANTHROPIC);
	}

	@Test
	void shouldBuildOptionsWithMultipleSkills() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.anthropicSkill(AnthropicSkill.XLSX)
			.anthropicSkill(AnthropicSkill.PPTX)
			.customSkill("my-custom-skill")
			.build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(3);
		assertThat(options.getSkillContainer().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().skills().get(1).skillId()).isEqualTo("pptx");
		assertThat(options.getSkillContainer().skills().get(2).skillId()).isEqualTo("my-custom-skill");
		assertThat(options.getSkillContainer().skills().get(2).type()).isEqualTo(SkillType.CUSTOM);
	}

	@Test
	void shouldBuildOptionsWithSkillContainer() {
		SkillContainer container = SkillContainer.builder().anthropicSkill(AnthropicSkill.DOCX).build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().skillContainer(container).build();

		assertThat(options.getSkillContainer()).isSameAs(container);
		assertThat(options.getSkillContainer().skills()).hasSize(1);
	}

	@Test
	void shouldBuildOptionsWithSkillVersion() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.anthropicSkill(AnthropicSkill.XLSX, "20251013")
			.build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(1);
		assertThat(options.getSkillContainer().skills().get(0).version()).isEqualTo("20251013");
	}

	@Test
	void shouldBuildOptionsWithCustomSkillVersion() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().customSkill("my-skill", "1.0.0").build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(1);
		assertThat(options.getSkillContainer().skills().get(0).skillId()).isEqualTo("my-skill");
		assertThat(options.getSkillContainer().skills().get(0).version()).isEqualTo("1.0.0");
		assertThat(options.getSkillContainer().skills().get(0).type()).isEqualTo(SkillType.CUSTOM);
	}

	@Test
	void shouldCopyOptionsWithSkills() {
		SkillContainer container = SkillContainer.builder().anthropicSkill(AnthropicSkill.PDF).build();

		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("claude-sonnet-4-5")
			.maxTokens(2048)
			.skillContainer(container)
			.build();

		AnthropicChatOptions copy = AnthropicChatOptions.fromOptions(original);

		assertThat(copy.getSkillContainer()).isNotNull();
		assertThat(copy.getSkillContainer()).isSameAs(original.getSkillContainer());
		assertThat(copy.getSkillContainer().skills()).hasSize(1);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getMaxTokens()).isEqualTo(original.getMaxTokens());
	}

	@Test
	void shouldIncludeSkillsInEqualsAndHashCode() {
		SkillContainer container = SkillContainer.builder().anthropicSkill(AnthropicSkill.XLSX).build();

		AnthropicChatOptions options1 = AnthropicChatOptions.builder().skillContainer(container).build();

		AnthropicChatOptions options2 = AnthropicChatOptions.builder().skillContainer(container).build();

		AnthropicChatOptions options3 = AnthropicChatOptions.builder().anthropicSkill(AnthropicSkill.PPTX).build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void shouldBuildOptionsWithSkillMethod() {
		Skill skill = new Skill(SkillType.ANTHROPIC, "docx", "latest");

		AnthropicChatOptions options = AnthropicChatOptions.builder().skill(skill).build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(1);
		assertThat(options.getSkillContainer().skills().get(0)).isSameAs(skill);
	}

	@Test
	void shouldAllowNullSkillContainer() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("claude-sonnet-4-5").build();

		assertThat(options.getSkillContainer()).isNull();
	}

	@Test
	void shouldAddMultipleSkillsSequentially() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.anthropicSkill(AnthropicSkill.XLSX)
			.anthropicSkill(AnthropicSkill.PPTX)
			.anthropicSkill(AnthropicSkill.DOCX)
			.build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(3);
	}

	@Test
	void shouldPreserveExistingSkillsWhenAddingNew() {
		SkillContainer initialContainer = SkillContainer.builder().anthropicSkill(AnthropicSkill.XLSX).build();

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.skillContainer(initialContainer)
			.anthropicSkill(AnthropicSkill.PPTX)
			.build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().skills()).hasSize(2);
		assertThat(options.getSkillContainer().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().skills().get(1).skillId()).isEqualTo("pptx");
	}

	@Test
	void shouldSetSkillContainerViaGetter() {
		AnthropicChatOptions options = new AnthropicChatOptions();
		SkillContainer container = SkillContainer.builder().anthropicSkill(AnthropicSkill.PDF).build();

		options.setSkillContainer(container);

		assertThat(options.getSkillContainer()).isSameAs(container);
	}

	@Test
	void shouldCopyOptionsWithNullSkills() {
		AnthropicChatOptions original = AnthropicChatOptions.builder().model("claude-sonnet-4-5").build();

		AnthropicChatOptions copy = AnthropicChatOptions.fromOptions(original);

		assertThat(copy.getSkillContainer()).isNull();
	}

	@Test
	void shouldMaintainSkillOrderWhenAdding() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.anthropicSkill(AnthropicSkill.XLSX)
			.customSkill("skill-a")
			.anthropicSkill(AnthropicSkill.PPTX)
			.customSkill("skill-b")
			.build();

		assertThat(options.getSkillContainer().skills()).hasSize(4);
		assertThat(options.getSkillContainer().skills().get(0).skillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().skills().get(1).skillId()).isEqualTo("skill-a");
		assertThat(options.getSkillContainer().skills().get(2).skillId()).isEqualTo("pptx");
		assertThat(options.getSkillContainer().skills().get(3).skillId()).isEqualTo("skill-b");
	}

}
