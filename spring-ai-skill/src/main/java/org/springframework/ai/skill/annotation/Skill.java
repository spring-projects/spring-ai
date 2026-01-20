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

package org.springframework.ai.skill.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation for marking a class as a Skill.
 *
 * <p>
 * Supports annotation mode (POJO) and interface mode (Skill implementation).
 *
 * @author LinPeng Zhang
 * @since 1.1.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Skill {

	/**
	 * Skill name (required).
	 * @return skill name
	 */
	String name();

	/**
	 * Skill description (required).
	 * @return skill description
	 */
	String description();

	/**
	 * Source identifier (optional, default "custom").
	 * @return source identifier
	 */
	String source() default "custom";

	/**
	 * Extension properties (optional).
	 *
	 * <p>
	 * Format: key=value
	 * @return extension properties array
	 */
	String[] extensions() default {};

}
