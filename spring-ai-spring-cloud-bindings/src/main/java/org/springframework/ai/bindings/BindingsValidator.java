/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bindings;

import org.springframework.core.env.Environment;

/**
 * From https://github.com/spring-cloud/spring-cloud-bindings to switch on/off the
 * bindings.
 */
final class BindingsValidator {

	static final String CONFIG_PATH = "spring.ai.cloud.bindings";

	/**
	 * Whether the given binding type should be used to contribute properties.
	 */
	static boolean isTypeEnabled(Environment environment, String type) {
		return environment.getProperty("%s.%s.enabled".formatted(CONFIG_PATH, type), Boolean.class, true);
	}

}
