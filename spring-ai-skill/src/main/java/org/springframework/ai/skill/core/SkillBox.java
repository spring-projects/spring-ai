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
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Tenant-level skill metadata container.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public interface SkillBox {

	/**
	 * Adds skill metadata.
	 * @param name skill name
	 * @param metadata skill metadata
	 * @throws IllegalArgumentException if name already exists
	 */
	void addSkill(String name, SkillMetadata metadata);

	/**
	 * Gets skill metadata.
	 * @param name skill name
	 * @return skill metadata or null if not found
	 */
	@Nullable SkillMetadata getMetadata(String name);

	/**
	 * Gets all skill metadata.
	 * @return unmodifiable map of name to metadata
	 */
	Map<String, SkillMetadata> getAllMetadata();

	/**
	 * Checks if skill exists.
	 * @param name skill name
	 * @return true if exists
	 */
	boolean exists(String name);

	/**
	 * Gets skill count.
	 * @return skill count
	 */
	int getSkillCount();

	/**
	 * Activates skill.
	 * @param name skill name
	 * @throws IllegalArgumentException if skill not found
	 */
	void activateSkill(String name);

	/**
	 * Deactivates skill.
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

	/**
	 * Gets activated skill names.
	 * @return set of activated skill names (never null)
	 */
	java.util.Set<String> getActivatedSkillNames();

	/**
	 * Gets supported skill sources in priority order.
	 * @return source list (mutable, ordered by priority)
	 */
	List<String> getSources();

}
