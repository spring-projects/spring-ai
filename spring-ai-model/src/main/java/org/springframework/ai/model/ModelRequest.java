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
 * Interface representing a request to an AI model. This interface encapsulates the
 * necessary information required to interact with an AI model, including instructions or
 * inputs (of generic type T) and additional model options. It provides a standardized way
 * to send requests to AI models, ensuring that all necessary details are included and can
 * be easily managed.
 *
 * @param <T> the type of instructions or input required by the AI model
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface ModelRequest<T> {

	/**
	 * Retrieves the instructions or input required by the AI model.
	 * @return the instructions or input required by the AI model
	 */
	T getInstructions(); // required input

	/**
	 * Retrieves the customizable options for AI model interactions.
	 * @return the customizable options for AI model interactions
	 */
	ModelOptions getOptions();

}
