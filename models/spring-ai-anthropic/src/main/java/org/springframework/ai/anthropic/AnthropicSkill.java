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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Enum representing the pre-built Anthropic Skills available for Claude.
 *
 * @author Soby Chacko
 */
public enum AnthropicSkill {

	/**
	 * Excel spreadsheet generation and manipulation.
	 */
	XLSX("xlsx", "Excel spreadsheet generation"),

	/**
	 * PowerPoint presentation creation.
	 */
	PPTX("pptx", "PowerPoint presentation creation"),

	/**
	 * Word document generation.
	 */
	DOCX("docx", "Word document generation"),

	/**
	 * PDF document creation.
	 */
	PDF("pdf", "PDF document creation");

	private static final Map<String, AnthropicSkill> BY_ID;

	static {
		Map<String, AnthropicSkill> map = new HashMap<>();
		for (AnthropicSkill skill : values()) {
			map.put(skill.skillId.toLowerCase(), skill);
		}
		BY_ID = Collections.unmodifiableMap(map);
	}

	private final String skillId;

	private final String description;

	AnthropicSkill(String skillId, String description) {
		this.skillId = skillId;
		this.description = description;
	}

	/**
	 * Look up a pre-built Anthropic skill by its ID.
	 * @param skillId the skill ID (e.g., "xlsx", "pptx", "docx", "pdf")
	 * @return the matching skill, or null if not found
	 */
	public static @Nullable AnthropicSkill fromId(@Nullable String skillId) {
		if (skillId == null) {
			return null;
		}
		return BY_ID.get(skillId.toLowerCase());
	}

	public String getSkillId() {
		return this.skillId;
	}

	public String getDescription() {
		return this.description;
	}

	/**
	 * Convert to an {@link AnthropicSkillRecord} with latest version.
	 * @return skill record
	 */
	public AnthropicSkillRecord toSkill() {
		return new AnthropicSkillRecord(AnthropicSkillType.ANTHROPIC, this.skillId, "latest");
	}

	/**
	 * Convert to an {@link AnthropicSkillRecord} with specific version.
	 * @param version version string
	 * @return skill record
	 */
	public AnthropicSkillRecord toSkill(String version) {
		return new AnthropicSkillRecord(AnthropicSkillType.ANTHROPIC, this.skillId, version);
	}

}
