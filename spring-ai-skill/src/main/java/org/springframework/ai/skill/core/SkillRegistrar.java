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
 * Skill registrar interface for registering skills from various sources.
 *
 * @param <T> source type (Class, Object, String, Path, etc.)
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public interface SkillRegistrar<T> {

	/**
	 * Creates SkillDefinition from source and registers to SkillPoolManager.
	 * @param poolManager skill pool manager
	 * @param source registration source
	 * @return created SkillDefinition
	 * @throws IllegalArgumentException if source invalid
	 * @throws org.springframework.ai.skill.exception.SkillRegistrationException if
	 * registration fails
	 */
	SkillDefinition register(SkillPoolManager poolManager, T source);

	/**
	 * Checks if registrar supports given source.
	 * @param source source object
	 * @return true if supported
	 */
	boolean supports(Object source);

	/**
	 * Gets registrar name for logging.
	 * @return registrar name
	 */
	default String getName() {
		return this.getClass().getSimpleName();
	}

}
