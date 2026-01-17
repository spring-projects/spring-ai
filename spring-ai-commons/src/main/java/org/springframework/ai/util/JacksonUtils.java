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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.Module;

import org.springframework.beans.BeanUtils;
import org.springframework.core.KotlinDetector;

/**
 * Utility methods for Jackson.
 *
 * @author Sebastien Deleuze
 */
public abstract class JacksonUtils {

	/**
	 * Instantiate well-known Jackson modules available in the classpath.
	 * <p>
	 * Supports the following modules: <code>Jdk8Module</code>,
	 * <code>JavaTimeModule</code>, <code>ParameterNamesModule</code> and
	 * <code>KotlinModule</code>.
	 * @return The list of instantiated modules.
	 */
	@SuppressWarnings("unchecked")
	public static List<Module> instantiateAvailableModules() {
		List<Module> modules = new ArrayList<>();
		try {
			Class<? extends com.fasterxml.jackson.databind.Module> jdk8ModuleClass = (Class<? extends Module>) Class
				.forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module");
			com.fasterxml.jackson.databind.Module jdk8Module = BeanUtils.instantiateClass(jdk8ModuleClass);
			modules.add(jdk8Module);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jdk8 not available
		}

		try {
			Class<? extends com.fasterxml.jackson.databind.Module> javaTimeModuleClass = (Class<? extends Module>) Class
				.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
			com.fasterxml.jackson.databind.Module javaTimeModule = BeanUtils.instantiateClass(javaTimeModuleClass);
			modules.add(javaTimeModule);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jsr310 not available
		}

		try {
			Class<? extends com.fasterxml.jackson.databind.Module> parameterNamesModuleClass = (Class<? extends Module>) Class
				.forName("com.fasterxml.jackson.module.paramnames.ParameterNamesModule");
			com.fasterxml.jackson.databind.Module parameterNamesModule = BeanUtils
				.instantiateClass(parameterNamesModuleClass);
			modules.add(parameterNamesModule);
		}
		catch (ClassNotFoundException ex) {
			// jackson-module-parameter-names not available
		}

		// Kotlin present?
		if (KotlinDetector.isKotlinPresent()) {
			try {
				Class<? extends com.fasterxml.jackson.databind.Module> kotlinModuleClass = (Class<? extends Module>) Class
					.forName("com.fasterxml.jackson.module.kotlin.KotlinModule");
				Module kotlinModule = BeanUtils.instantiateClass(kotlinModuleClass);
				modules.add(kotlinModule);
			}
			catch (ClassNotFoundException ex) {
				// jackson-module-kotlin not available
			}
		}
		return modules;
	}

}
