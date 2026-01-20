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

import java.util.Collections;
import java.util.List;

import org.springframework.ai.tool.ToolCallback;

/**
 * Core Skill interface.
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
public interface Skill {

	/**
	 * Gets skill metadata.
	 * @return skill metadata
	 */
	SkillMetadata getMetadata();

	/**
	 * Gets skill name.
	 * @return skill name
	 */
	default String getName() {
		return this.getMetadata().getName();
	}

	/**
	 * Gets skill description.
	 * @return skill description
	 */
	default String getDescription() {
		return this.getMetadata().getDescription();
	}

	/**
	 * Gets skill content in Markdown format.
	 * @return skill content
	 */
	String getContent();

	/**
	 * Gets tool callbacks provided by this skill.
	 * @return tool callback list (never null, defaults to empty)
	 */
	default List<ToolCallback> getTools() {
		return Collections.emptyList();
	}

	/**
	 * Checks if this skill supports the specified capability interface.
	 * @param capabilityType capability interface class
	 * @param <T> capability type
	 * @return true if supported
	 */
	default <T> boolean supports(Class<T> capabilityType) {
		return capabilityType.isInstance(this);
	}

	/**
	 * Converts this skill to the specified capability interface.
	 * @param capabilityType capability interface class
	 * @param <T> capability type
	 * @return capability instance
	 * @throws ClassCastException if capability not supported
	 */
	default <T> T as(Class<T> capabilityType) {
		if (!this.supports(capabilityType)) {
			throw new ClassCastException(
					"Skill '" + this.getName() + "' does not support capability: " + capabilityType.getName());
		}
		return capabilityType.cast(this);
	}

}
