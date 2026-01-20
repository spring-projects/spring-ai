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

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.skill.exception.SkillLoadException;
import org.springframework.ai.skill.exception.SkillNotFoundException;

/**
 * Skill pool manager for definition storage and instance lifecycle management.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public interface SkillPoolManager {

	/**
	 * Registers skill definition.
	 * @param definition skill definition
	 * @throws IllegalArgumentException if skillId already exists
	 */
	void registerDefinition(SkillDefinition definition);

	/**
	 * Unregisters skill completely (definition and instance).
	 * @param skillId skill ID
	 */
	void unregister(String skillId);

	/**
	 * Gets skill definition.
	 * @param skillId skill ID
	 * @return skill definition or null if not found
	 */
	@Nullable SkillDefinition getDefinition(String skillId);

	/**
	 * Checks if skill definition exists.
	 * @param skillId skill ID
	 * @return true if exists
	 */
	boolean hasDefinition(String skillId);

	/**
	 * Loads skill instance (singleton per skillId).
	 * @param skillId skill ID
	 * @return skill instance
	 * @throws SkillNotFoundException if skill not found
	 * @throws SkillLoadException if loading fails
	 */
	Skill load(String skillId);

	/**
	 * Gets all registered skill definitions.
	 * @return skill definition list (never null)
	 */
	List<SkillDefinition> getDefinitions();

	/**
	 * Evicts skill instance but retains definition.
	 * @param skillId skill ID
	 */
	void evict(String skillId);

	/**
	 * Evicts all skill instances but retains definitions.
	 */
	void evictAll();

	/**
	 * Clears all definitions and instances.
	 */
	void clear();

}
