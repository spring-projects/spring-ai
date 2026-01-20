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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.ToolCallback;

/**
 * Unified coordination interface for skill management.
 *
 * @author LinPeng Zhang
 * @see DefaultSkillKit
 * @since 1.1.3
 */
public interface SkillKit {

	// ==================== Skill Registration ====================

	/**
	 * Registers skill with metadata and lazy loader.
	 * @param metadata skill metadata
	 * @param loader skill instance loader
	 * @throws org.springframework.ai.skill.exception.SkillValidationException if
	 * validation fails
	 * @throws org.springframework.ai.skill.exception.SkillRegistrationException if
	 * registration fails
	 */
	void register(SkillMetadata metadata, Supplier<Skill> loader);

	/**
	 * Registers skill from instance.
	 * @param instance skill instance with @Skill annotation
	 * @throws IllegalArgumentException if instance class lacks @Skill annotation
	 */
	void register(Object instance);

	/**
	 * Registers skill from class with lazy loading.
	 * @param skillClass skill class with @Skill annotation
	 * @throws IllegalArgumentException if class lacks @Skill annotation
	 */
	void register(Class<?> skillClass);

	// ==================== Skill Access ====================

	/**
	 * Checks if skill exists by name.
	 * @param name skill name
	 * @return true if exists
	 */
	boolean exists(String name);

	/**
	 * Gets skill instance by ID.
	 * @param skillId skill ID
	 * @return skill instance
	 * @throws org.springframework.ai.skill.exception.SkillNotFoundException if not found
	 * @throws org.springframework.ai.skill.exception.SkillLoadException if loading fails
	 */
	Skill getSkill(String skillId);

	/**
	 * Gets skill instance by name.
	 * @param name skill name
	 * @return skill instance or null if not found
	 */
	@Nullable Skill getSkillByName(String name);

	/**
	 * Gets skill metadata by name.
	 * @param name skill name
	 * @return skill metadata or null if not found
	 */
	@Nullable SkillMetadata getMetadata(String name);

	// ==================== Skill Activation ====================

	/**
	 * Activates skill by name.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found
	 */
	void activateSkill(String name);

	/**
	 * Deactivates skill by name.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found
	 */
	void deactivateSkill(String name);

	/**
	 * Deactivates all skills.
	 */
	void deactivateAllSkills();

	/**
	 * Checks if skill is activated.
	 * @param name skill name
	 * @return true if activated
	 */
	boolean isActivated(String name);

	// ==================== Tools ====================

	/**
	 * Gets framework skill tools for progressive loading.
	 * @return skill tool list
	 */
	List<ToolCallback> getSkillLoaderTools();

	/**
	 * Gets all active skill tools.
	 * @return active tool list (never null)
	 * @throws IllegalStateException if data inconsistency detected
	 */
	List<ToolCallback> getAllActiveTools();

	/**
	 * Gets system prompt describing available skills.
	 * @return system prompt (never null)
	 */
	String getSkillSystemPrompt();

}
