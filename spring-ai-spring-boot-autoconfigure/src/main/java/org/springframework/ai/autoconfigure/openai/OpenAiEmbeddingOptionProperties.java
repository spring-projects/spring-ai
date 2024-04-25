/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.openai.OpenAiEmbeddingOptions;

public class OpenAiEmbeddingOptionProperties extends OpenAiEmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-ada-002";

	private String model = DEFAULT_EMBEDDING_MODEL;

	private String encodingFormat;

	private String user;

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(final String model) {
		this.model = model;
	}

	@Override
	public String getEncodingFormat() {
		return this.encodingFormat;
	}

	public void setEncodingFormat(final String encodingFormat) {
		this.encodingFormat = encodingFormat;
	}

	@Override
	public String getUser() {
		return this.user;
	}

	public void setUser(final String user) {
		this.user = user;
	}

}
