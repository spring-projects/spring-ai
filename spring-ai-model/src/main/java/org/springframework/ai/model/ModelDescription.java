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

package org.springframework.ai.model;

/**
 * Describes an AI model's basic characteristics. Provides methods to retrieve the model's
 * name, description, and version.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface ModelDescription {

	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	String getName();

	/**
	 * Returns the description of the model.
	 * @return the description of the model
	 */
	default String getDescription() {
		return "";
	}

	/**
	 * Returns the version of the model.
	 * @return the version of the model
	 */
	default String getVersion() {
		return "";
	}

}
