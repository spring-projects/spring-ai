/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.support;

import java.util.Objects;

import org.springframework.ai.skill.core.SkillIdGenerator;
import org.springframework.ai.skill.core.SkillMetadata;

/**
 * Default skill ID generator using "{name}_{source}" format.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public class DefaultSkillIdGenerator implements SkillIdGenerator {

	/**
	 * Generates skill ID in format: {name}_{source}.
	 * @param metadata skill metadata
	 * @return generated skill ID
	 * @throws IllegalArgumentException if metadata invalid
	 */
	@Override
	public String generateId(SkillMetadata metadata) {
		Objects.requireNonNull(metadata, "metadata cannot be null");

		String name = metadata.getName();
		String source = metadata.getSource();

		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("metadata.name cannot be null or empty");
		}

		if (source == null || source.trim().isEmpty()) {
			throw new IllegalArgumentException("metadata.source cannot be null or empty");
		}

		return name + "_" + source;
	}

}
