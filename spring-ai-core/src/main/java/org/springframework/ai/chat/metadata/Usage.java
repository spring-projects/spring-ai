/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.chat.metadata;

/**
 * Abstract Data Type (ADT) encapsulating metadata on the usage of an AI provider's API
 * per AI request.
 *
 * @author John Blum
 * @since 0.7.0
 */
public interface Usage {

	Usage NULL = new AbstractUsage() {
	};

	/**
	 * Returns the number of tokens used in the {@literal prompt} of the AI request.
	 * @return an {@link Long} with the number of tokens used in the {@literal prompt} of
	 * the AI request.
	 * @see #getGenerationTokens()
	 */
	Long getPromptTokens();

	/**
	 * Returns the number of tokens returned in the {@literal generation (aka completion)}
	 * of the AI's response.
	 * @return an {@link Long} with the number of tokens returned in the
	 * {@literal generation (aka completion)} of the AI's response.
	 * @see #getPromptTokens()
	 */
	Long getGenerationTokens();

	/**
	 * Return the total number of tokens from both the {@literal prompt} of an AI request
	 * and {@literal generation} of the AI's response.
	 * @return the total number of tokens from both the {@literal prompt} of an AI request
	 * and {@literal generation} of the AI's response.
	 * @see #getPromptTokens()
	 * @see #getGenerationTokens()
	 */
	default Long getTotalTokens() {
		Long promptTokens = getPromptTokens();
		promptTokens = promptTokens != null ? promptTokens : 0;
		Long completionTokens = getGenerationTokens();
		completionTokens = completionTokens != null ? completionTokens : 0;
		return promptTokens + completionTokens;
	}

}
