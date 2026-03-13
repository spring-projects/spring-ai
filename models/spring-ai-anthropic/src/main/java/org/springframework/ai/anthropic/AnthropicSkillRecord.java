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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Represents a Claude Skill - either pre-built Anthropic skill or custom skill. Skills
 * are collections of instructions, scripts, and resources that extend Claude's
 * capabilities for specific domains.
 *
 * @author Soby Chacko
 */
public class AnthropicSkillRecord {

	private final AnthropicSkillType type;

	private final String skillId;

	private final String version;

	/**
	 * Create a skill with a specific version.
	 * @param type skill type
	 * @param skillId skill identifier
	 * @param version version string (e.g., "latest", "20251013")
	 */
	public AnthropicSkillRecord(AnthropicSkillType type, String skillId, String version) {
		Assert.notNull(type, "Skill type cannot be null");
		Assert.hasText(skillId, "Skill ID cannot be empty");
		Assert.hasText(version, "Version cannot be empty");
		this.type = type;
		this.skillId = skillId;
		this.version = version;
	}

	/**
	 * Create a skill with default "latest" version.
	 * @param type skill type
	 * @param skillId skill identifier
	 */
	public AnthropicSkillRecord(AnthropicSkillType type, String skillId) {
		this(type, skillId, "latest");
	}

	public AnthropicSkillType getType() {
		return this.type;
	}

	public String getSkillId() {
		return this.skillId;
	}

	public String getVersion() {
		return this.version;
	}

	/**
	 * Convert to a map suitable for JSON serialization via {@code JsonValue.from()}.
	 * @return map with type, skill_id, and version keys
	 */
	public Map<String, Object> toJsonMap() {
		return Map.of("type", this.type.getValue(), "skill_id", this.skillId, "version", this.version);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable AnthropicSkillType type;

		private @Nullable String skillId;

		private String version = "latest";

		public Builder type(AnthropicSkillType type) {
			this.type = type;
			return this;
		}

		public Builder skillId(String skillId) {
			this.skillId = skillId;
			return this;
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public AnthropicSkillRecord build() {
			Assert.notNull(this.type, "Skill type cannot be null");
			Assert.hasText(this.skillId, "Skill ID cannot be empty");
			return new AnthropicSkillRecord(this.type, this.skillId, this.version);
		}

	}

}
