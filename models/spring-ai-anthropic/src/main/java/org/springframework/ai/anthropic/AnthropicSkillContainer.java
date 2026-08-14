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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Container for Claude Skills in a chat completion request. Maximum of 8 skills per
 * request.
 *
 * @author Soby Chacko
 */
public class AnthropicSkillContainer {

	private final List<AnthropicSkillRecord> skills;

	public AnthropicSkillContainer(List<AnthropicSkillRecord> skills) {
		Assert.notNull(skills, "Skills list cannot be null");
		Assert.notEmpty(skills, "Skills list cannot be empty");
		if (skills.size() > 8) {
			throw new IllegalArgumentException("Maximum of 8 skills per request. Provided: " + skills.size());
		}
		this.skills = Collections.unmodifiableList(new ArrayList<>(skills));
	}

	public List<AnthropicSkillRecord> getSkills() {
		return this.skills;
	}

	/**
	 * Convert to a list of maps suitable for JSON serialization via
	 * {@code JsonValue.from(Map.of("skills", container.toSkillsList()))}.
	 * @return list of skill maps with type, skill_id, and version keys
	 */
	public List<Map<String, Object>> toSkillsList() {
		return this.skills.stream().map(AnthropicSkillRecord::toJsonMap).toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final List<AnthropicSkillRecord> skills = new ArrayList<>();

		/**
		 * Add a skill by its ID or name. Automatically detects whether it's a pre-built
		 * Anthropic skill (xlsx, pptx, docx, pdf) or a custom skill ID.
		 * @param skillIdOrName the skill ID or name
		 * @return this builder
		 */
		public Builder skill(String skillIdOrName) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			AnthropicSkill prebuilt = AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill());
			}
			return this.skill(new AnthropicSkillRecord(AnthropicSkillType.CUSTOM, skillIdOrName));
		}

		/**
		 * Add a skill by its ID or name with a specific version.
		 * @param skillIdOrName the skill ID or name
		 * @param version the version (e.g., "latest", "20251013")
		 * @return this builder
		 */
		public Builder skill(String skillIdOrName, String version) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			Assert.hasText(version, "Version cannot be empty");
			AnthropicSkill prebuilt = AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill(version));
			}
			return this.skill(new AnthropicSkillRecord(AnthropicSkillType.CUSTOM, skillIdOrName, version));
		}

		/**
		 * Add a pre-built Anthropic skill using the enum.
		 * @param skill the Anthropic skill enum value
		 * @return this builder
		 */
		public Builder skill(AnthropicSkill skill) {
			Assert.notNull(skill, "AnthropicSkill cannot be null");
			return this.skill(skill.toSkill());
		}

		/**
		 * Add a pre-built Anthropic skill with a specific version.
		 * @param skill the Anthropic skill enum value
		 * @param version the version
		 * @return this builder
		 */
		public Builder skill(AnthropicSkill skill, String version) {
			Assert.notNull(skill, "AnthropicSkill cannot be null");
			Assert.hasText(version, "Version cannot be empty");
			return this.skill(skill.toSkill(version));
		}

		/**
		 * Add a skill record directly.
		 * @param skill the skill record
		 * @return this builder
		 */
		public Builder skill(AnthropicSkillRecord skill) {
			Assert.notNull(skill, "Skill cannot be null");
			this.skills.add(skill);
			return this;
		}

		/**
		 * Add multiple skills by their IDs or names.
		 * @param skillIds the skill IDs or names
		 * @return this builder
		 */
		public Builder skills(String... skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			for (String skillId : skillIds) {
				this.skill(skillId);
			}
			return this;
		}

		/**
		 * Add multiple skills from a list of IDs or names.
		 * @param skillIds the list of skill IDs or names
		 * @return this builder
		 */
		public Builder skills(List<String> skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			skillIds.forEach(this::skill);
			return this;
		}

		public AnthropicSkillContainer build() {
			return new AnthropicSkillContainer(new ArrayList<>(this.skills));
		}

	}

}
