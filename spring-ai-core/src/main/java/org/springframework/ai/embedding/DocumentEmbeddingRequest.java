/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.embedding;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.ModelRequest;

/**
 * Represents a request to embed a list of documents.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DocumentEmbeddingRequest implements ModelRequest<List<Document>> {

	private final List<Document> inputs;

	private final EmbeddingOptions options;

	public DocumentEmbeddingRequest(Document... inputs) {
		this(Arrays.asList(inputs), EmbeddingOptionsBuilder.builder().build());
	}

	public DocumentEmbeddingRequest(List<Document> inputs) {
		this(inputs, EmbeddingOptionsBuilder.builder().build());
	}

	public DocumentEmbeddingRequest(List<Document> inputs, EmbeddingOptions options) {
		this.inputs = inputs;
		this.options = options;
	}

	@Override
	public List<Document> getInstructions() {
		return this.inputs;
	}

	@Override
	public EmbeddingOptions getOptions() {
		return this.options;
	}

}
