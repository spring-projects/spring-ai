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

package org.springframework.ai.google.genai.text;

import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.retry.support.RetryTemplate;

/**
 * Test implementation of GoogleGenAiTextEmbeddingModel that uses a mock connection for
 * testing purposes.
 *
 * @author Dan Dobrin
 */
public class TestGoogleGenAiTextEmbeddingModel extends GoogleGenAiTextEmbeddingModel {

	public TestGoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails,
			GoogleGenAiTextEmbeddingOptions defaultEmbeddingOptions, RetryTemplate retryTemplate) {
		super(connectionDetails, defaultEmbeddingOptions, retryTemplate);
	}

	/**
	 * For testing purposes, expose the default options.
	 */
	public GoogleGenAiTextEmbeddingOptions getDefaultOptions() {
		return this.defaultOptions;
	}

}
