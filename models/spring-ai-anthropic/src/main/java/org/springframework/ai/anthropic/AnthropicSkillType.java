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

/**
 * Enum representing the type of a Claude Skill.
 *
 * @author Soby Chacko
 */
public enum AnthropicSkillType {

	/**
	 * Pre-built skills provided by Anthropic (xlsx, pptx, docx, pdf).
	 */
	ANTHROPIC("anthropic"),

	/**
	 * Custom skills uploaded to the workspace.
	 */
	CUSTOM("custom");

	private final String value;

	AnthropicSkillType(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
