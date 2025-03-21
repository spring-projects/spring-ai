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

package org.springframework.ai.embedding;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.core.io.DefaultResourceLoader;

/**
 * Abstract implementation of the {@link EmbeddingModel} interface that provides
 * dimensions calculation caching.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractEmbeddingModel implements EmbeddingModel {

	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = loadKnownModelDimensions();

	/**
	 * Default constructor.
	 */
	public AbstractEmbeddingModel() {
	}

	/**
	 * Cached embedding dimensions.
	 */
	protected final AtomicInteger embeddingDimensions = new AtomicInteger(-1);

	/**
	 * Return the dimension of the requested embedding generative name. If the generative
	 * name is unknown uses the EmbeddingModel to perform a dummy EmbeddingModel#embed and
	 * count the response dimensions.
	 * @param embeddingModel Fall-back client to determine, empirically the dimensions.
	 * @param modelName Embedding generative name to retrieve the dimensions for.
	 * @param dummyContent Dummy content to use for the empirical dimension calculation.
	 * @return Returns the embedding dimensions for the modelName.
	 */
	public static int dimensions(EmbeddingModel embeddingModel, String modelName, String dummyContent) {

		if (KNOWN_EMBEDDING_DIMENSIONS.containsKey(modelName)) {
			// Retrieve the dimension from a pre-configured file.
			return KNOWN_EMBEDDING_DIMENSIONS.get(modelName);
		}
		else {
			// Determine the dimensions empirically.
			// Generate an embedding and count the dimension size;
			return embeddingModel.embed(dummyContent).length;
		}
	}

	private static Map<String, Integer> loadKnownModelDimensions() {
		try {
			Properties properties = new Properties();
			properties.load(new DefaultResourceLoader()
				.getResource("classpath:/embedding/embedding-model-dimensions.properties")
				.getInputStream());
			return properties.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> e.getKey().toString(), e -> Integer.parseInt(e.getValue().toString())));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int dimensions() {
		if (this.embeddingDimensions.get() < 0) {
			this.embeddingDimensions.set(dimensions(this, "Test", "Hello World"));
		}
		return this.embeddingDimensions.get();
	}

}
