/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.util;

import java.util.List;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.MapperBuilder;

/**
 * Utility methods for Jackson.
 *
 * @author Sebastien Deleuze
 */
public abstract class JacksonUtils {

	/**
	 * Return the Jackson modules found by {@link MapperBuilder#findModules(ClassLoader)}.
	 * @return The list of instantiated modules.
	 */
	public static List<JacksonModule> instantiateAvailableModules() {
		return MapperBuilder.findModules(JacksonUtils.class.getClassLoader());
	}

}
