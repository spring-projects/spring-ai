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

package org.springframework.ai.skill.core;

/**
 * Skill ID generator interface.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
@FunctionalInterface
public interface SkillIdGenerator {

	/**
	 * Generates unique skill ID from metadata.
	 * @param metadata skill metadata
	 * @return generated skill ID
	 * @throws IllegalArgumentException if metadata invalid
	 */
	String generateId(SkillMetadata metadata);

}
