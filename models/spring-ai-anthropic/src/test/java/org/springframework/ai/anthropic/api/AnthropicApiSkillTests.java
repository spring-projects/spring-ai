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

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicSkill;
import org.springframework.ai.anthropic.api.AnthropicApi.Skill;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillContainer;
import org.springframework.ai.anthropic.api.AnthropicApi.SkillType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Anthropic Skills API models.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class AnthropicApiSkillTests {

	@Test
	void shouldCreateAnthropicSkill() {
		Skill skill = Skill.builder().type(SkillType.ANTHROPIC).skillId("xlsx").version("20251013").build();

		assertThat(skill.type()).isEqualTo(SkillType.ANTHROPIC);
		assertThat(skill.skillId()).isEqualTo("xlsx");
		assertThat(skill.version()).isEqualTo("20251013");
	}

	@Test
	void shouldCreateCustomSkill() {
		Skill skill = Skill.builder().type(SkillType.CUSTOM).skillId("custom-skill-id-12345").version("latest").build();

		assertThat(skill.type()).isEqualTo(SkillType.CUSTOM);
		assertThat(skill.skillId()).isEqualTo("custom-skill-id-12345");
		assertThat(skill.version()).isEqualTo("latest");
	}

	@Test
	void shouldDefaultToLatestVersion() {
		Skill skill = new Skill(SkillType.ANTHROPIC, "xlsx");
		assertThat(skill.version()).isEqualTo("latest");
	}

	@Test
	void shouldCreateFromAnthropicSkillEnum() {
		Skill skill = AnthropicSkill.XLSX.toSkill();

		assertThat(skill.type()).isEqualTo(SkillType.ANTHROPIC);
		assertThat(skill.skillId()).isEqualTo("xlsx");
		assertThat(skill.version()).isEqualTo("latest");
	}

	@Test
	void shouldCreateFromAnthropicSkillEnumWithVersion() {
		Skill skill = AnthropicSkill.PPTX.toSkill("20251013");

		assertThat(skill.type()).isEqualTo(SkillType.ANTHROPIC);
		assertThat(skill.skillId()).isEqualTo("pptx");
		assertThat(skill.version()).isEqualTo("20251013");
	}

	@Test
	void shouldFailWhenSkillTypeIsNull() {
		assertThatThrownBy(() -> Skill.builder().skillId("xlsx").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Skill type cannot be null");
	}

	@Test
	void shouldFailWhenSkillIdIsEmpty() {
		assertThatThrownBy(() -> Skill.builder().type(SkillType.ANTHROPIC).skillId("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Skill ID cannot be empty");
	}

	@Test
	void shouldCreateContainerWithSingleSkill() {
		SkillContainer container = SkillContainer.builder().skill("xlsx").build();

		assertThat(container.skills()).hasSize(1);
		assertThat(container.skills().get(0).skillId()).isEqualTo("xlsx");
	}

	@Test
	void shouldCreateContainerWithMultipleSkills() {
		SkillContainer container = SkillContainer.builder()
			.skill(AnthropicSkill.XLSX)
			.skill(AnthropicSkill.PPTX)
			.skill("company-guidelines")
			.build();

		assertThat(container.skills()).hasSize(3);
		assertThat(container.skills()).extracting(Skill::skillId).containsExactly("xlsx", "pptx", "company-guidelines");
	}

	@Test
	void shouldEnforceMaximum8Skills() {
		SkillContainer.SkillContainerBuilder builder = SkillContainer.builder();

		// Add 9 skills
		for (int i = 0; i < 9; i++) {
			builder.skill("skill-" + i);
		}

		assertThatThrownBy(() -> builder.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Maximum of 8 skills per request");
	}

	@Test
	void shouldFailWithEmptySkillsList() {
		assertThatThrownBy(() -> new SkillContainer(List.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Skills list cannot be empty");
	}

	@Test
	void shouldFailWithNullSkillsList() {
		assertThatThrownBy(() -> new SkillContainer(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Skills list cannot be null");
	}

	@Test
	void shouldAllowExactly8Skills() {
		SkillContainer.SkillContainerBuilder builder = SkillContainer.builder();

		for (int i = 0; i < 8; i++) {
			builder.skill("skill-" + i);
		}

		SkillContainer container = builder.build();
		assertThat(container.skills()).hasSize(8);
	}

	@Test
	void shouldGetSkillIdFromAnthropicSkillEnum() {
		assertThat(AnthropicSkill.XLSX.getSkillId()).isEqualTo("xlsx");
		assertThat(AnthropicSkill.PPTX.getSkillId()).isEqualTo("pptx");
		assertThat(AnthropicSkill.DOCX.getSkillId()).isEqualTo("docx");
		assertThat(AnthropicSkill.PDF.getSkillId()).isEqualTo("pdf");
	}

	@Test
	void shouldGetDescriptionFromAnthropicSkillEnum() {
		assertThat(AnthropicSkill.XLSX.getDescription()).isEqualTo("Excel spreadsheet generation");
		assertThat(AnthropicSkill.PPTX.getDescription()).isEqualTo("PowerPoint presentation creation");
		assertThat(AnthropicSkill.DOCX.getDescription()).isEqualTo("Word document generation");
		assertThat(AnthropicSkill.PDF.getDescription()).isEqualTo("PDF document creation");
	}

	@Test
	void shouldGetValueFromSkillTypeEnum() {
		assertThat(SkillType.ANTHROPIC.getValue()).isEqualTo("anthropic");
		assertThat(SkillType.CUSTOM.getValue()).isEqualTo("custom");
	}

}
